package com.relationship.graph.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.StagedDecision
import com.relationship.graph.ui.GedcomIntent
import com.relationship.graph.ui.GedcomUiState
import com.relationship.graph.data.backup.BackupManager
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingOperation {
    EXPORT,
    RESTORE,
}

@Composable
fun BackupScreen(
    state: AppUiState,
    viewModel: RelationshipViewModel,
    onBack: () -> Unit,
) {
    var passwordDialog by remember { mutableStateOf<PendingOperation?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri, password)
        }
        password = ""
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            passwordDialog = PendingOperation.RESTORE
        }
    }
    val gedcomExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.exportGedcom(uri)
    }
    val gedcomImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onGedcomIntent(GedcomIntent.Stage(uri))
    }
    val gedcomState by viewModel.gedcomUiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "备份与恢复",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "当前数据：${state.people.size} 人，${state.relationships.size} 条关系",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "导出的备份会使用单独密码加密。请妥善保管密码，忘记后无法恢复备份。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    password = ""
                    error = null
                    passwordDialog = PendingOperation.EXPORT
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("导出加密备份")
            }
            OutlinedButton(
                onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("从备份恢复")
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                text = "GEDCOM 互通",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "导出标准 .ged 家谱文件；导入的数据先逐条确认（自动识别重复人物），确认后才会写入。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    gedcomExportLauncher.launch("family-tree-$date.ged")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("导出 GEDCOM (.ged)")
            }
            OutlinedButton(
                onClick = {
                    gedcomImportLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("导入 GEDCOM (.ged)")
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    passwordDialog?.let { operation ->
        AlertDialog(
            onDismissRequest = {
                passwordDialog = null
                password = ""
                error = null
            },
            title = {
                Text(if (operation == PendingOperation.EXPORT) "设置备份密码" else "输入备份密码")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        label = {
                            Text(
                                if (operation == PendingOperation.EXPORT) {
                                    "至少 6 位密码"
                                } else {
                                    "备份密码"
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        error = if (password.length < 6) "密码至少需要 6 位" else null
                        if (error != null) return@TextButton
                        when (operation) {
                            PendingOperation.EXPORT -> {
                                val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                                    .format(Date())
                                passwordDialog = null
                                createDocumentLauncher.launch("relationship-$date.rgbackup")
                            }
                            PendingOperation.RESTORE -> {
                                passwordDialog = null
                                confirmRestore = true
                            }
                        }
                    },
                ) {
                    Text(if (operation == PendingOperation.EXPORT) "选择保存位置" else "继续")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        passwordDialog = null
                        password = ""
                        error = null
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("替换现有数据") },
            text = { Text("恢复后将删除当前 App 内的全部人物和关系，并替换为备份内容。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedRestoreUri?.let { viewModel.restoreBackup(it, password) }
                        confirmRestore = false
                        password = ""
                        selectedRestoreUri = null
                    },
                ) {
                    Text("确认恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            },
        )
    }

    if (gedcomState.batchId != null) {
        GedcomReviewDialog(
            state = gedcomState,
            onIntent = viewModel::onGedcomIntent,
        )
    }
}

/** GEDCOM 导入审查：逐条决定 新建 / 并入已有（自动建议）/ 忽略，确认后才写库。 */
@Composable
private fun GedcomReviewDialog(
    state: GedcomUiState,
    onIntent: (GedcomIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.working && state.batchId != null) onIntent(GedcomIntent.Discard) },
        title = { Text("确认 GEDCOM 导入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "文件：${state.fileName}，解析出 ${state.personCount} 人、" +
                        "${state.relationshipCount} 条家庭结构。以下为待确认人物：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.stagedPeople.isEmpty()) {
                    Text("没有解析出人物。")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(state.stagedPeople, key = { it.id }) { staged ->
                            val mergeTarget = staged.mergePersonId ?: staged.suggestedMergePersonId
                            val mergeName = when (staged.decision) {
                                StagedDecision.MERGE -> "并入"
                                StagedDecision.IGNORE -> "忽略"
                                else -> "新建"
                            }
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    text = staged.name + when {
                                        staged.gender == Gender.MALE -> "（男）"
                                        staged.gender == Gender.FEMALE -> "（女）"
                                        else -> ""
                                    } + if (staged.birthDate.isNotBlank()) " ${staged.birthDate}" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        StagedDecision.CREATE to "新建",
                                        StagedDecision.MERGE to if (mergeTarget != null) "并入已有" else "并入（无建议）",
                                        StagedDecision.IGNORE to "忽略",
                                    ).forEach { (decision, label) ->
                                        FilterChip(
                                            selected = staged.decision == decision,
                                            onClick = { onIntent(GedcomIntent.SetDecision(staged.id, decision)) },
                                            label = { Text(label) },
                                            enabled = decision != StagedDecision.MERGE || mergeTarget != null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.working && state.stagedPeople.isNotEmpty(),
                onClick = { onIntent(GedcomIntent.Apply) },
            ) { Text(if (state.working) "正在写入…" else "确认写入") }
        },
        dismissButton = {
            TextButton(
                enabled = !state.working,
                onClick = { onIntent(GedcomIntent.Discard) },
            ) { Text("放弃") }
        },
    )
}
