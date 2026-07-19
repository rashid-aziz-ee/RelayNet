package com.example.relaynet.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.relaynet.data.Message
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: RelayViewModel) {
    val messages by viewModel.messages.collectAsState()
    val peers by viewModel.connectedPeers.collectAsState()
    val selectedRecipientId by viewModel.selectedRecipientId.collectAsState()
    val selectedRecipientName by viewModel.selectedRecipientName.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Filter messages to show ONLY ours, broadcasts, or directed to us
    val visibleMessages = remember(messages, selectedRecipientId) {
        messages.filter {
            it.isBroadcast || it.senderId == viewModel.myDeviceId || it.recipientId == viewModel.myDeviceId || it.recipientId.isEmpty()
        }
    }

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    // Connectivity indicator pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "connectivityPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RelayNet",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "// MESH",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Text(
                            text = "STATUS: ${if (peers.isNotEmpty()) "LINK ACTIVE" else "SCANNING..."}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (peers.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                },
                actions = {
                    // NEW: Clear Chat button - only shown when there's actually something to clear
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAllMessages() },
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Connected peers count badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(
                            1.dp,
                            if (peers.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // Pulsing Dot
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
                                if (peers.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .graphicsLayer(
                                                scaleX = pulseScale,
                                                scaleY = pulseScale,
                                                alpha = pulseAlpha
                                            )
                                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (peers.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${peers.size} ${if (peers.size == 1) "PEER" else "PEERS"}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (peers.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(12.dp)
                ) {
                    // Directed recipient alert banner
                    if (selectedRecipientId.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🔒", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SECURE E2EE CHAT WITH: ${selectedRecipientName.uppercase()}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearRecipient() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Text(
                                        text = "✕",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (peers.isEmpty()) {
                        // Warning if no peers are connected - Warm Amber style
                        Surface(
                            color = Color(0xFF1E150B),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Text(
                                    text = "⚠️ NO PEERS DETECTED. Messages will be saved in local storage and relayed automatically upon node connection.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Red SOS button for Emergency Broadcast
                        Button(
                            onClick = {
                                viewModel.onSendMessage("🆘 SOS — EMERGENCY BROADCAST FROM ${viewModel.myName}!")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 1.dp
                            ),
                            modifier = Modifier
                                .height(56.dp)
                                .padding(end = 8.dp)
                                .testTag("sos_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Emergency SOS",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Type an offline message...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val isInputEmpty = inputText.trim().isEmpty()

                        IconButton(
                            onClick = {
                                if (!isInputEmpty) {
                                    viewModel.onSendMessage(inputText.trim())
                                    inputText = ""
                                }
                            },
                            enabled = !isInputEmpty,
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = if (!isInputEmpty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (!isInputEmpty) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live Peers indicator horizontal row
            if (peers.isNotEmpty()) {
                PeersRow(
                    peers = peers,
                    selectedRecipientId = selectedRecipientId,
                    onPeerClick = { id, name ->
                        if (selectedRecipientId == id) {
                            viewModel.clearRecipient()
                        } else {
                            viewModel.selectRecipient(id, name)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            // Message list or empty state
            if (visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "📡",
                                    fontSize = 32.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No messages yet",
                            modifier = Modifier.testTag("empty_state"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your device is scanning for nearby nodes. Send an SOS or type a message to cache it for mesh relay.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("message_list"),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleMessages, key = { it.messageId }) { message ->
                        MessageBubble(
                            message = message,
                            myDeviceId = viewModel.myDeviceId,
                            // NEW: long-press a bubble to delete just that message
                            onLongPress = { viewModel.deleteMessage(message.messageId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeersRow(
    peers: Map<String, String>,
    selectedRecipientId: String,
    onPeerClick: (String, String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "peerDot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "peerPulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .testTag("peers_row")
    ) {
        Text(
            text = "CONNECTED MESH PEERS (TAP TO START DIRECT E2EE CHAT)",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(peers.entries.toList(), key = { it.key }) { (endpointId, displayName) ->
                val isSelected = selectedRecipientId == endpointId

                Surface(
                    onClick = { onPeerClick(endpointId, displayName) },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 1f else pulseAlpha),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(${endpointId.take(4)})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// NEW: onLongPress parameter added - triggers deletion of this single message.
// @OptIn(ExperimentalFoundationApi::class) is required for combinedClickable (onLongClick).
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, myDeviceId: String, onLongPress: () -> Unit = {}) {
    val isMe = message.isMine

    val bubbleColor = if (message.isBroadcast) {
        MaterialTheme.colorScheme.tertiaryContainer // Alert red container
    } else if (isMe) {
        MaterialTheme.colorScheme.primaryContainer // Signal green container
    } else {
        MaterialTheme.colorScheme.surfaceVariant // Slate grey container
    }

    val bubbleBorder = if (message.isBroadcast) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary) // Alert red border
    } else if (isMe) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
    }

    val textColor = if (message.isBroadcast) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else if (isMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val alignment = if (isMe) Alignment.End else Alignment.Start
    val shape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        // Sender Name (if not me)
        if (!isMe) {
            Text(
                text = message.senderName.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                ),
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }

        // Message body - NEW: combinedClickable added so long-press triggers deletion.
        // onClick is a no-op (required by combinedClickable) so normal taps still behave
        // exactly as before; only a long-press does anything new.
        Surface(
            color = bubbleColor,
            shape = shape,
            border = bubbleBorder,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = { /* no-op: tapping a bubble does nothing, same as before */ },
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Emergency broadcast label
                if (message.isBroadcast && message.payload.startsWith("🆘")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EMERGENCY BROADCAST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.tertiary,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                } else if (!message.isBroadcast) {
                    // E2EE directed message label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(text = "🔒", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURE DIRECT MESSAGE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                // Message payload
                Text(
                    text = message.payload,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Metadata block (Hop count, Delivery Status, Time)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    val timeString = remember(message.originTimestamp) {
                        timeFormat.format(Date(message.originTimestamp))
                    }

                    // Hop Count
                    Surface(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "VIA ${message.hopCount} HOP${if (message.hopCount != 1) "S" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            color = textColor.copy(alpha = 0.4f)
                        )
                    )

                    // Delivery Status
                    val statusColor = when (message.deliveryStatus) {
                        "SENDING" -> MaterialTheme.colorScheme.secondary // Amber
                        "SENT" -> MaterialTheme.colorScheme.primary // Signal Green
                        "RELAYED" -> MaterialTheme.colorScheme.primary // Signal Green
                        else -> textColor.copy(alpha = 0.8f)
                    }
                    Text(
                        text = message.deliveryStatus,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            color = textColor.copy(alpha = 0.4f)
                        )
                    )

                    // Time
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    }
}