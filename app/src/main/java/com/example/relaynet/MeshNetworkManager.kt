package com.example.relaynet

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

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

    fun broadcastNewMessage(text: String, myName: String) {
        val envelope = MessageEnvelope(senderId = myDeviceId, senderName = myName, payload = text)
        seenMessageIds.add(envelope.messageId)

        val messageRawString = "${envelope.messageId}|${envelope.senderId}|${envelope.senderName}|${envelope.payload}|${envelope.timestamp}|${envelope.ttl}"
        sendToAllPeers(messageRawString, null)
    }

    private fun handleIncomingRawData(rawData: String, cameFromEndpoint: String) {
        try {
            val parts = rawData.split("|")
            if (parts.size < 6) return

            val mId = parts[0]
            if (seenMessageIds.contains(mId)) return

            seenMessageIds.add(mId)

            val envelope = MessageEnvelope(
                messageId = mId,
                senderId = parts[1],
                senderName = parts[2],
                payload = parts[3],
                timestamp = parts[4].toLong(),
                ttl = parts[5].toInt()
            )

            onMessageReceivedCallback(envelope)

            if (envelope.ttl > 1) {
                val nextTtl = envelope.ttl - 1
                val relayedRawString = "${envelope.messageId}|${envelope.senderId}|${envelope.senderName}|${envelope.payload}|${envelope.timestamp}|$nextTtl"
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