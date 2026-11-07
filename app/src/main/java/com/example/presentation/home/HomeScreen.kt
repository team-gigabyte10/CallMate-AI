package com.example.presentation.home

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ActionType
import com.example.presentation.components.AiProposalDialog
import com.example.presentation.components.TaskCard
import com.example.presentation.components.VoiceMicButton
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.ReminderAmber
import com.example.ui.theme.SmsBlue
import com.example.ui.theme.WhatsAppGreen
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val upcomingTasks by viewModel.upcomingTasks.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val aiProposal by viewModel.aiProposal.collectAsStateWithLifecycle()
    val commandInput by viewModel.commandInput.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showChatHistory by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic greeting calculation
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Greeting and stats
            item {
                GreetingHeader(
                    greeting = greeting,
                    todayCount = todayTasks.count { it.status != com.example.domain.model.TaskStatus.COMPLETED },
                    upcomingCount = upcomingTasks.size
                )
            }

            // AI Assistant Panel
            item {
                AiAssistantPanel(
                    isListening = isListening,
                    conversations = conversations,
                    commandInput = commandInput,
                    showChatHistory = showChatHistory,
                    onToggleChatHistory = { showChatHistory = !showChatHistory },
                    onCommandChange = viewModel::onCommandInputChange,
                    onSend = { viewModel.processCommand() },
                    onMicClick = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onQuickPrompt = { prompt ->
                        viewModel.processCommand(prompt)
                    }
                )
            }

            // Quick Action Buttons (Call, SMS, WhatsApp, Reminder)
            item {
                QuickActionSection(onActionClick = viewModel::createQuickTask)
            }

            // Today's Tasks
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Tasks",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${todayTasks.count { it.status == com.example.domain.model.TaskStatus.COMPLETED }}/${todayTasks.size} Done",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (todayTasks.isEmpty()) {
                item {
                    EmptyStateCard(message = "No tasks scheduled for today. Ask CallMate to schedule a call or reminder!")
                }
            } else {
                items(todayTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onPrimaryAction = { viewModel.executeTask(task) },
                        onComplete = { viewModel.completeTask(task.id) },
                        onSnooze = { viewModel.snoozeTask(task.id) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }

            // Upcoming Reminders
            if (upcomingTasks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Upcoming Communication",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(upcomingTasks.take(5), key = { "upcoming_${it.id}" }) { task ->
                    TaskCard(
                        task = task,
                        onPrimaryAction = { viewModel.executeTask(task) },
                        onComplete = { viewModel.completeTask(task.id) },
                        onSnooze = { viewModel.snoozeTask(task.id) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }

        // Proposal Confirmation Dialog
        aiProposal?.let { proposal ->
            AiProposalDialog(
                proposal = proposal,
                onConfirm = { confirmed ->
                    viewModel.confirmProposal(confirmed)
                },
                onDismiss = { viewModel.dismissProposal() }
            )
        }
    }
}

@Composable
fun GreetingHeader(
    greeting: String,
    todayCount: Int,
    upcomingCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            IndigoPrimary.copy(alpha = 0.12f),
                            CyanAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$greeting 👋",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "CallMate AI",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(IndigoPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Active",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill(
                        label = "Due Today",
                        value = "$todayCount",
                        color = if (todayCount > 0) CyanAccent else IndigoPrimary
                    )
                    StatPill(
                        label = "Scheduled",
                        value = "$upcomingCount",
                        color = IndigoPrimary
                    )
                    StatPill(
                        label = "Languages",
                        value = "EN / BN",
                        color = CallGreen
                    )
                }
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$value ",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AiAssistantPanel(
    isListening: Boolean,
    conversations: List<com.example.data.local.entities.ConversationEntity>,
    commandInput: String,
    showChatHistory: Boolean,
    onToggleChatHistory: () -> Unit,
    onCommandChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with AI Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isListening) CallGreen else CyanAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "Listening to voice..." else "Personal AI Assistant",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable(onClick = onToggleChatHistory)
                ) {
                    Text(
                        text = if (showChatHistory) "Hide History" else "History (${conversations.size})",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = IndigoPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Conversation History
            AnimatedVisibility(visible = showChatHistory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    conversations.takeLast(6).forEach { chat ->
                        val isUser = chat.sender == "USER"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUser) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = chat.message,
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Latest AI prompt message if chat history hidden
            if (!showChatHistory && conversations.isNotEmpty()) {
                val lastMsg = conversations.last()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${if (lastMsg.sender == "USER") "You" else "CallMate"}: ${lastMsg.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                        maxLines = 2
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Voice Mic and Text Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceMicButton(
                    isListening = isListening,
                    onClick = onMicClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = commandInput,
                    onValueChange = onCommandChange,
                    placeholder = {
                        Text(
                            text = if (isListening) "Speak now..." else "E.g. Call Rahim tomorrow 2 PM",
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_command_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (commandInput.isNotBlank()) {
                            IconButton(
                                onClick = onSend,
                                modifier = Modifier.testTag("send_command_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send Command",
                                    tint = IndigoPrimary
                                )
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Prompt Chips
            Text(
                text = "Try saying or tapping:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val prompts = listOf(
                    "Call Rahim tomorrow at 2 PM",
                    "Ammu k rat 8 tay call dao",
                    "WhatsApp Boss status update at 5 PM",
                    "SMS Tanvir I am on my way",
                    "Doctor appointment reminder in 30 mins"
                )
                items(prompts) { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.clickable { onQuickPrompt(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionSection(
    onActionClick: (ActionType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionButton(
            label = "Schedule Call",
            icon = Icons.Default.Call,
            color = CallGreen,
            onClick = { onActionClick(ActionType.CALL) },
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "Draft SMS",
            icon = Icons.Default.Send,
            color = SmsBlue,
            onClick = { onActionClick(ActionType.SMS) },
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "WhatsApp",
            icon = Icons.Outlined.Chat,
            color = WhatsAppGreen,
            onClick = { onActionClick(ActionType.WHATSAPP) },
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "Reminder",
            icon = Icons.Default.Notifications,
            color = ReminderAmber,
            onClick = { onActionClick(ActionType.REMINDER) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
