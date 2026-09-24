package com.relationship.graph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.relationship.graph.ui.AiAssistantUiState
import com.relationship.graph.ui.AiChatMessage
import com.relationship.graph.ui.AiMessageRole
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState

@Composable
fun AiAssistantScreen(
    state: AiAssistantUiState,
    onSend: (String) -> Unit,
    onConfirmAction: (messageId: String, actionId: String) -> Unit,
    onRejectAction: (messageId: String, actionId: String) -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AI 助手",
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "清空对话")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "AI 设置")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.settings.isReady) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = "AI 尚未配置",
                    detail = "填写兼容 OpenAI 的接口地址、模型和 API Key 后即可使用。",
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text("配置 AI")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    AiMessageBubble(
                        message = message,
                        onConfirmAction = onConfirmAction,
                        onRejectAction = onRejectAction,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("询问关系或描述要修改的内容") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        onSend(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !state.isSending,
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.padding(10.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMessageBubble(
    message: AiChatMessage,
    onConfirmAction: (messageId: String, actionId: String) -> Unit,
    onRejectAction: (messageId: String, actionId: String) -> Unit,
) {
    val alignment = if (message.role == AiMessageRole.USER) {
        Alignment.End
    } else {
        Alignment.Start
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            color = when (message.role) {
                AiMessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
                AiMessageRole.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
                AiMessageRole.ASSISTANT -> MaterialTheme.colorScheme.surface
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            tonalElevation = 2.dp,
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        message.proposedActions.forEach { action ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(action.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        action.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { onRejectAction(message.id, action.id) }) {
                            Text("忽略")
                        }
                        Button(onClick = { onConfirmAction(message.id, action.id) }) {
                            Text("确认执行")
                        }
                    }
                }
            }
        }
    }
}
