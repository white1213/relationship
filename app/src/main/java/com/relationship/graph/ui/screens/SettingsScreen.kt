package com.relationship.graph.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relationship.graph.BuildConfig
import com.relationship.graph.RelationshipApplication
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.MyPersonPickerDialog
import com.relationship.graph.ui.security.rememberBiometricPromptLauncher

@Composable
fun SettingsScreen(
    state: AppUiState,
    app: RelationshipApplication,
    onOpenBackup: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onSetMyPerson: (String) -> Unit,
    onLock: () -> Unit,
) {
    val lockState by app.appLockController.state.collectAsStateWithLifecycle()
    var showMyPersonDialog by remember { mutableStateOf(false) }
    val enableBiometric = rememberBiometricPromptLauncher(
        title = "启用生物识别",
        onSuccess = { app.appLockController.setBiometricEnabled(true) },
    )

    Scaffold(
        topBar = { AppTopBar(title = "设置") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("安全", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLock() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Text(
                            text = "立即锁定",
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp),
                        )
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp),
                        ) {
                            Text("指纹或面容解锁")
                            Text(
                                text = if (lockState.biometricAvailable) {
                                    "在设备支持时使用"
                                } else {
                                    "当前设备不支持"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = lockState.biometricEnabled && lockState.biometricAvailable,
                            enabled = lockState.biometricAvailable,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    enableBiometric()
                                } else {
                                    app.appLockController.setBiometricEnabled(false)
                                }
                            },
                        )
                    }
                }
            }

            Text("我的设置", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.people.isNotEmpty()) {
                        showMyPersonDialog = true
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                    ) {
                        Text("我的信息")
                        Text(
                            text = state.myPersonId
                                ?.let { personId -> state.person(personId)?.name }
                                ?: if (state.people.isEmpty()) "请先添加人物" else "尚未设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }

            Text("数据", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBackup),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Backup, contentDescription = null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                    ) {
                        Text("备份与恢复")
                        Text(
                            text = "${state.people.size} 人，${state.relationships.size} 条关系",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }

            Text("AI", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAiSettings),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                    ) {
                        Text("AI 设置")
                        Text(
                            text = "配置兼容 OpenAI 的 API 服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }

            Text("关于", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("关系图谱")
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "关系数据默认保存在本机；启用 AI 后会按设置发送关系摘要。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showMyPersonDialog) {
        MyPersonPickerDialog(
            people = state.people,
            selectedPersonId = state.myPersonId,
            onSelect = { personId ->
                onSetMyPerson(personId)
                showMyPersonDialog = false
            },
            onDismiss = { showMyPersonDialog = false },
        )
    }
}
