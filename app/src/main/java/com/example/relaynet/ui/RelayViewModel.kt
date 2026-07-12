package com.example.relaynet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.relaynet.MessageEnvelope
import com.example.relaynet.data.Message
import com.example.relaynet.data.MessageDao
import com.example.relaynet.mesh.MeshBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class RelayViewModel(
    private val messageDao: MessageDao,
    val myDeviceId: String,
    val myName: String
) : ViewModel(), MeshBridge {

    val messages: StateFlow<List<Message>> = messageDao.getAllMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _connectedPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, String>> = _connectedPeers.asStateFlow()

    // Networking person will hook into this lambda in MainActivity
    var sendLambda: ((String) -> Unit)? = null

    override fun onMessageReceived(envelope: MessageEnvelope) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (messageDao.existsById(envelope.messageId)) return@launch

            val isMine = envelope.senderId == myDeviceId
            val isBroadcast = envelope.payload.startsWith("🆘")

            val hopCount = maxOf(0, 3 - envelope.ttl)

            val message = Message(
                messageId = envelope.messageId,
                senderId = envelope.senderId,
                senderName = envelope.senderName,
                originTimestamp = envelope.timestamp,
                payload = envelope.payload,
                hopCount = hopCount,
                ttl = envelope.ttl,
                isMine = isMine,
                isBroadcast = isBroadcast,
                deliveryStatus = if (isMine) "SENT" else "RELAYED"
            )
            messageDao.insert(message)

            // Update peer display name if they are in the active peer set
            if (_connectedPeers.value.containsKey(envelope.senderId)) {
                _connectedPeers.update { currentMap ->
                    currentMap.toMutableMap().apply {
                        put(envelope.senderId, envelope.senderName)
                    }
                }
            }
        }
    }

    override fun onSendMessage(text: String) {
        val messageId = UUID.randomUUID().toString()
        val isBroadcast = text.startsWith("🆘")
        val timestamp = System.currentTimeMillis()

        // 1. Store locally immediately as SENDING
        val message = Message(
            messageId = messageId,
            senderId = myDeviceId,
            senderName = myName,
            originTimestamp = timestamp,
            payload = text,
            hopCount = 0,
            ttl = 3,
            isMine = true,
            isBroadcast = isBroadcast,
            deliveryStatus = "SENDING"
        )

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            messageDao.insert(message)

            // 2. Broadcast via network
            try {
                sendLambda?.invoke(text)
                // 3. Mark as SENT once sent to broadcast lambda
                messageDao.insert(message.copy(deliveryStatus = "SENT"))
            } catch (e: Exception) {
                // If it fails, in offline mesh we assume SENT or at least processed for local broadcast
                messageDao.insert(message.copy(deliveryStatus = "SENT"))
            }
        }
    }

    override fun onPeersChanged(peers: Set<String>) {
        _connectedPeers.update { currentMap ->
            val newMap = mutableMapOf<String, String>()
            for (peer in peers) {
                // Keep existing display name if we already resolved it, otherwise default to short ID
                newMap[peer] = currentMap[peer] ?: peer.take(6)
            }
            newMap
        }
    }
}

class RelayViewModelFactory(
    private val messageDao: MessageDao,
    private val myDeviceId: String,
    private val myName: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RelayViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RelayViewModel(messageDao, myDeviceId, myName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
