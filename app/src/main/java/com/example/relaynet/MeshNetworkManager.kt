package com.example.relaynet

import android.content.Context
import android.util.Base64
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MeshNetworkManager(
    private val context: Context,
    private val myDeviceId: String,
    private val onMessageReceivedCallback: (MessageEnvelope) -> Unit
) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.relaynet.MESH_SERVICE"
    private val STRATEGY = Strategy.P2P_CLUSTER

    private val connectedPeers = mutableSetOf<String>()
    private val seenMessageIds = mutableSetOf<String>()

    // --- AES Encryption Keys & Parameters ---
    private val SECRET_KEY = "MCS_NUST_CHEE7AH" // 16 bytes key for AES-128
    private val ALGORITHM = "AES/CBC/PKCS5Padding"
    private val ivBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15) // Fixed IV for MVP

    // --- Crypto Helpers (Lock/Unlock) ---
    private fun encrypt(plainText: String): String {
        val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
    }

    private fun decrypt(encryptedText: String): String {
        val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes)
    }

    // --- Core Network Operations ---
    fun startMesh() {
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(myDeviceId, SERVICE_ID, connectionLifecycleCallback, options)
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
    }

    // 1. Send Karte Waqt Message ko Encrypt (Lock) Karna
    fun broadcastNewMessage(text: String, myName: String) {
        try {
            val encryptedPayload = encrypt(text)
            val envelope = MessageEnvelope(senderId = myDeviceId, senderName = myName, payload = encryptedPayload)
            seenMessageIds.add(envelope.messageId)

            val messageRawString = "${envelope.messageId}|${envelope.senderId}|${envelope.senderName}|${envelope.payload}|${envelope.timestamp}|${envelope.ttl}"
            sendToAllPeers(messageRawString, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. Receive Karte Waqt Message ko Decrypt (Unlock) Karna
    private fun handleIncomingRawData(rawData: String, cameFromEndpoint: String) {
        try {
            val parts = rawData.split("|")
            if (parts.size < 6) return

            val mId = parts[0]
            if (seenMessageIds.contains(mId)) return

            seenMessageIds.add(mId)

            val encryptedPayload = parts[3]
            // Safe decryption, agar koi error aaye toh backup text dikhaye ga
            val decryptedPayload = try {
                decrypt(encryptedPayload)
            } catch (e: Exception) {
                "[Encrypted Message Data Intercepted]"
            }

            val envelope = MessageEnvelope(
                messageId = mId,
                senderId = parts[1],
                senderName = parts[2],
                payload = decryptedPayload, // Person 2 ki UI ko normal un-locked text mile ga
                timestamp = parts[4].toLong(),
                ttl = parts[5].toInt()
            )

            onMessageReceivedCallback(envelope)

            // Relaying / Forwarding logic to other nodes
            if (envelope.ttl > 1) {
                val nextTtl = envelope.ttl - 1
                // Relayed packet keeps the encrypted payload intact
                val relayedRawString = "${envelope.messageId}|${envelope.senderId}|${envelope.senderName}|$encryptedPayload|${envelope.timestamp}|$nextTtl"
                sendToAllPeers(relayedRawString, cameFromEndpoint)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendToAllPeers(dataStr: String, excludeEndpoint: String?) {
        val payload = Payload.fromBytes(dataStr.toByteArray())
        for (peerId in connectedPeers) {
            if (peerId != excludeEndpoint) {
                connectionsClient.sendPayload(peerId, payload)
            }
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

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedPeers.add(endpointId)
            }
        }
        override fun onDisconnected(endpointId: String) {
            connectedPeers.remove(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(myDeviceId, endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }
}