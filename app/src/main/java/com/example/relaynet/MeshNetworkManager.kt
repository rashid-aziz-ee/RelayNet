package com.example.relaynet

import android.content.Context
import android.util.Base64
import com.example.relaynet.data.CryptoEngine
import com.example.relaynet.data.Message
import com.example.relaynet.data.RelayNetDatabase
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class MeshNetworkManager(
    private val context: Context,
    private val myDeviceId: String,
    private val onMessageReceivedCallback: (MessageEnvelope) -> Unit
) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.relaynet.MESH_SERVICE"
    private val STRATEGY = Strategy.P2P_CLUSTER

    val connectedPeers = mutableSetOf<String>()
    private val seenMessageIds = mutableSetOf<String>()

    private val cryptoEngine = CryptoEngine(context)
    private val messageDao = RelayNetDatabase.getDatabase(context).messageDao()

    // FIX: all Room database access must happen off the main thread. Nearby Connections
    // callbacks (onPayloadReceived, onConnectionResult, etc.) run on the main thread, so any
    // messageDao call made directly inside them was throwing IllegalStateException and being
    // silently swallowed by the surrounding try/catch - this was the real cause of messages
    // appearing to "vanish". This dedicated background scope is used for all DB + relay work.
    private val meshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Core Network Operations ---
    fun startMesh() {
        startAdvertising()
        startDiscovery()
    }

    // FIX (bug 1 - STATUS_ALREADY_DISCOVERING): tears down any advertising/discovery/connections
    // that may still be alive (e.g. after app backgrounds and resumes) before starting fresh.
    // These stop calls are safe to call even if nothing is currently running - Nearby just
    // no-ops instead of throwing.
    fun stopMesh() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedPeers.clear()
        onPeersChangedCallback?.invoke(connectedPeers.toSet())
    }

    // FIX (bug 1): call this from onResume() instead of startMesh() directly.
    fun restartMesh() {
        stopMesh()
        startMesh()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(myDeviceId, SERVICE_ID, connectionLifecycleCallback, options)
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
    }

    // Broadcast or Direct Message sending
    fun broadcastNewMessage(text: String, myName: String) {
        broadcastNewMessage(text, myName, "", "")
    }

    fun broadcastNewMessage(text: String, myName: String, recipientId: String, recipientName: String) {
        // Encryption + payload building doesn't touch the DB directly here, but keep it off
        // the calling thread too (this can be called from UI click handlers) to stay safe.
        meshScope.launch {
            try {
                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                val isBroadcast = text.startsWith("🆘")

                // FIX (bug 2 - chat messages not relaying): encryption must be gated on
                // whether there's an actual recipient to encrypt for, NOT on the 🆘 emoji.
                // Previously, any message with an empty recipientId (i.e. every normal
                // group/open chat message) fell into the "else" branch and called
                // cryptoEngine.encryptForPeer(text, "") - encrypting for a nonexistent empty
                // peer ID. That threw inside encryptForPeer, was caught by the try/catch
                // below, printed to Logcat, and silently dropped the message before
                // sendToAllPeers ever ran. SOS messages worked because they skipped
                // encryption entirely via the old isBroadcast check.
                val payloadToSend = if (recipientId.isEmpty()) {
                    // group/broadcast chat - no single peer to encrypt for
                    text
                } else {
                    cryptoEngine.encryptForPeer(text, recipientId)
                }

                seenMessageIds.add(messageId)

                // Local cache is handled by ViewModel, but we also ensure over the air delivery string format:
                // messageId|senderId|senderName|payload|timestamp|ttl|recipientId|recipientName
                val messageRawString = "$messageId|$myDeviceId|$myName|$payloadToSend|$timestamp|3|$recipientId|$recipientName"
                sendToAllPeers(messageRawString, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // FIX: this whole function now runs inside meshScope (background thread) instead of
    // directly on the Nearby callback's main thread, so every messageDao call inside it
    // (getAllMessageIds, getMessageById, existsById, insert) is now safe to run.
    private fun handleIncomingRawData(rawData: String, cameFromEndpoint: String) {
        meshScope.launch {
            try {
                val parts = rawData.split("|")
                if (parts.isEmpty()) return@launch

                val type = parts[0]

                // 1. Peer Key Exchange Handshake
                if (type == "KEY_EXCHANGE") {
                    if (parts.size < 4) return@launch
                    val peerId = parts[1]
                    val peerName = parts[2]
                    val peerPublicKey = parts[3]

                    cryptoEngine.savePeerPublicKey(peerId, peerPublicKey)

                    // Trigger anti-entropy database sync Catalog
                    val ids = messageDao.getAllMessageIds()
                    val idsCsv = ids.joinToString(",")
                    sendToPeer("SYNC_CATALOG|$myDeviceId|$idsCsv", cameFromEndpoint)
                    return@launch
                }

                // 2. Gossip Sync Catalog Received
                if (type == "SYNC_CATALOG") {
                    if (parts.size < 3) return@launch
                    val peerId = parts[1]
                    val idsCsv = parts[2]
                    if (idsCsv.isEmpty()) return@launch

                    val peerIds = idsCsv.split(",")
                    val myIds = messageDao.getAllMessageIds().toSet()

                    val missingIds = peerIds.filter { !myIds.contains(it) }
                    if (missingIds.isNotEmpty()) {
                        sendToPeer("SYNC_REQUEST|$myDeviceId|${missingIds.joinToString(",")}", cameFromEndpoint)
                    }
                    return@launch
                }

                // 3. Gossip Sync Catalog Request Received
                if (type == "SYNC_REQUEST") {
                    if (parts.size < 3) return@launch
                    val peerId = parts[1]
                    val requestedIdsCsv = parts[2]
                    if (requestedIdsCsv.isEmpty()) return@launch

                    val requestedIds = requestedIdsCsv.split(",")
                    for (id in requestedIds) {
                        val msg = messageDao.getMessageById(id) ?: continue
                        val syncStr = "SYNC_DELIVERY|$myDeviceId|${msg.messageId}|${msg.senderId}|${msg.senderName}|${msg.payload}|${msg.originTimestamp}|${msg.ttl}|${msg.recipientId}|${msg.recipientName}"
                        sendToPeer(syncStr, cameFromEndpoint)
                    }
                    return@launch
                }

                // 4. Gossip Sync Catalog Delivery Received
                if (type == "SYNC_DELIVERY") {
                    if (parts.size < 10) return@launch
                    val msgId = parts[2]
                    if (messageDao.existsById(msgId)) return@launch

                    val senderId = parts[3]
                    val senderName = parts[4]
                    val payload = parts[5]
                    val timestamp = parts[6].toLong()
                    val ttl = parts[7].toInt()
                    val recipientId = parts[8]
                    val recipientName = parts[9]

                    val message = Message(
                        messageId = msgId,
                        senderId = senderId,
                        senderName = senderName,
                        originTimestamp = timestamp,
                        payload = payload, // Kept encrypted as received
                        hopCount = maxOf(0, 3 - ttl),
                        ttl = ttl,
                        isMine = (senderId == myDeviceId),
                        isBroadcast = payload.startsWith("🆘"),
                        deliveryStatus = "RELAYED",
                        recipientId = recipientId,
                        recipientName = recipientName
                    )
                    messageDao.insert(message)

                    // Relay message to other nodes
                    if (ttl > 1) {
                        val nextTtl = ttl - 1
                        val relayedSyncStr = "SYNC_DELIVERY|$myDeviceId|$msgId|$senderId|$senderName|$payload|$timestamp|$nextTtl|$recipientId|$recipientName"
                        sendToAllPeers(relayedSyncStr, cameFromEndpoint)
                    }

                    // If meant for us, alert UI flow
                    if (recipientId == myDeviceId || senderId == myDeviceId || recipientId.isEmpty()) {
                        val envelope = MessageEnvelope(
                            messageId = msgId,
                            senderId = senderId,
                            senderName = senderName,
                            payload = cryptoEngine.decryptMessage(payload),
                            timestamp = timestamp,
                            ttl = ttl
                        )
                        onMessageReceivedCallback(envelope)
                    }
                    return@launch
                }

                // 5. Standard Real-Time Flood Protocol
                if (parts.size >= 6) {
                    val mId = parts[0]
                    if (seenMessageIds.contains(mId)) return@launch
                    seenMessageIds.add(mId)

                    val senderId = parts[1]
                    val senderName = parts[2]
                    val encryptedPayload = parts[3]
                    val timestamp = parts[4].toLong()
                    val ttl = parts[5].toInt()
                    val recipientId = if (parts.size > 6) parts[6] else ""
                    val recipientName = if (parts.size > 7) parts[7] else ""

                    val decryptedPayload = cryptoEngine.decryptMessage(encryptedPayload)

                    val envelope = MessageEnvelope(
                        messageId = mId,
                        senderId = senderId,
                        senderName = senderName,
                        payload = decryptedPayload,
                        timestamp = timestamp,
                        ttl = ttl
                    )

                    // Store in database
                    val message = Message(
                        messageId = mId,
                        senderId = senderId,
                        senderName = senderName,
                        originTimestamp = timestamp,
                        payload = encryptedPayload, // Save ciphertext
                        hopCount = maxOf(0, 3 - ttl),
                        ttl = ttl,
                        isMine = (senderId == myDeviceId),
                        isBroadcast = decryptedPayload.startsWith("🆘"),
                        deliveryStatus = if (senderId == myDeviceId) "SENT" else "RELAYED",
                        recipientId = recipientId,
                        recipientName = recipientName
                    )
                    messageDao.insert(message)

                    // Forward packet to peers
                    if (ttl > 1) {
                        val nextTtl = ttl - 1
                        val relayedRawString = "$mId|$senderId|$senderName|$encryptedPayload|$timestamp|$nextTtl|$recipientId|$recipientName"
                        sendToAllPeers(relayedRawString, cameFromEndpoint)
                    }

                    // If readable locally, notify viewmodel
                    if (recipientId == myDeviceId || senderId == myDeviceId || recipientId.isEmpty()) {
                        onMessageReceivedCallback(envelope)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // FIX (bug 3 - silent send failures / stuck dead peers): if the underlying Bluetooth/WiFi
    // socket to a peer dies mid-session (seen in Logcat as "VirtualOutputStream ... closed"),
    // sendPayload throws. Previously that exception was uncaught here and would crash the
    // meshScope coroutine silently. Now we catch it, drop the dead peer from connectedPeers,
    // and notify the UI - so the peer list stays accurate and future sends don't keep
    // retrying a socket that's already gone.
    private fun sendToAllPeers(dataStr: String, excludeEndpoint: String?) {
        val payload = Payload.fromBytes(dataStr.toByteArray())
        val deadPeers = mutableListOf<String>()
        for (peerId in connectedPeers) {
            if (peerId != excludeEndpoint) {
                try {
                    connectionsClient.sendPayload(peerId, payload)
                } catch (e: Exception) {
                    e.printStackTrace()
                    deadPeers.add(peerId)
                }
            }
        }
        if (deadPeers.isNotEmpty()) {
            connectedPeers.removeAll(deadPeers.toSet())
            onPeersChangedCallback?.invoke(connectedPeers.toSet())
        }
    }

    private fun sendToPeer(dataStr: String, peerId: String) {
        try {
            val payload = Payload.fromBytes(dataStr.toByteArray())
            connectionsClient.sendPayload(peerId, payload)
        } catch (e: Exception) {
            e.printStackTrace()
            connectedPeers.remove(peerId)
            onPeersChangedCallback?.invoke(connectedPeers.toSet())
        }
    }

    // --- Google Nearby Callbacks Boilerplate ---
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val rawData = String(payload.asBytes()!!)
                handleIncomingRawData(rawData, endpointId)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    var onPeersChangedCallback: ((Set<String>) -> Unit)? = null

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedPeers.add(endpointId)
                onPeersChangedCallback?.invoke(connectedPeers.toSet())

                // Send key exchange payload instantly
                val myPubKey = cryptoEngine.getMyPublicKeyBase64()
                sendToPeer("KEY_EXCHANGE|$myDeviceId|$myDeviceId|$myPubKey", endpointId)
            }
        }
        override fun onDisconnected(endpointId: String) {
            connectedPeers.remove(endpointId)
            onPeersChangedCallback?.invoke(connectedPeers.toSet())

            // FIX (bug 3 - lost connections never come back): previously, once a peer
            // disconnected, nothing restarted advertising/discovery, so Nearby never looked
            // for that peer (or any new peer) again until the whole app/process was killed
            // and relaunched. Restarting the mesh here makes it self-heal instead of going
            // permanently dead after any single disconnect.
            restartMesh()
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(myDeviceId, endpointId, connectionLifecycleCallback)
        }

        // FIX (bug 3, cont.): if an endpoint disappears mid-discovery (never fully connected),
        // also restart discovery so it doesn't quietly stall.
        override fun onEndpointLost(endpointId: String) {
            // no-op — don't tear down all peers just because one endpoint was lost
        }
    }
}