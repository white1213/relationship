package com.relationship.graph.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.relationship.graph.data.kinship.KinshipGroup
import com.relationship.graph.data.kinship.KinshipParseResult
import com.relationship.graph.data.kinship.KinshipTermCatalog
import com.relationship.graph.data.kinship.KinshipTermSpec
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.PersonAvatar
import kotlinx.coroutines.launch

/** 句式主语写「我」时绑定到设置里唯一的「我」。 */
internal fun kinshipPersonFor(state: AppUiState, name: String): PersonEntity? {
    val trimmed = name.trim()
    if (trimmed == "我" || trimmed == "自己") {
        return state.myPersonId?.let { id -> state.people.firstOrNull { it.id == id } }
    }
    return state.people.firstOrNull { it.name == trimmed }
}

/**
 * 一句话录入的预览确认弹窗：解析结果一目了然，
 * 库里没有的人物标出来并可在确认时补建（只填姓名和性别）。
 */
@Composable
fun KinshipSentenceDialog(
    parsed: KinshipParseResult.Success,
    state: AppUiState,
    viewModel: RelationshipViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var subjectGender by remember { mutableStateOf(Gender.UNSPECIFIED) }
    var relativeGender by remember { mutableStateOf(Gender.UNSPECIFIED) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val subjectPerson = kinshipPersonFor(state, parsed.subjectName)
    val relativePerson = kinshipPersonFor(state, parsed.relativeName)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("确认添加关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${parsed.subjectName} 是 ${parsed.relativeName} 的${parsed.spec.term}",
                    style = MaterialTheme.typography.titleMedium,
                )
                KinshipPersonResolutionRow(
                    name = parsed.subjectName,
                    person = subjectPerson,
                    gender = subjectGender,
                    onGenderChange = { subjectGender = it },
                )
                KinshipPersonResolutionRow(
                    name = parsed.relativeName,
                    person = relativePerson,
                    gender = relativeGender,
                    onGenderChange = { relativeGender = it },
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        saving = true
                        val result = viewModel.commitKinshipEntry(
                            subjectName = parsed.subjectName,
                            relativeName = parsed.relativeName,
                            spec = parsed.spec,
                            subjectPersonId = subjectPerson?.id,
                            relativePersonId = relativePerson?.id,
                            subjectGender = subjectGender,
                            relativeGender = relativeGender,
                        )
                        saving = false
                        if (result == null) {
                            onDismiss()
                        } else {
                            error = result
                        }
                    }
                },
                enabled = !saving,
            ) {
                Text(if (saving) "正在保存…" else "确认写入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 详情页快建面板：先选类别和称谓，再选或新建对方人物，
 * 方向按称谓自动确定，确认前显示完整预览句。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddKinshipDialog(
    person: PersonEntity,
    state: AppUiState,
    viewModel: RelationshipViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(KinshipGroup.DIRECT) }
    var selectedSpec by remember { mutableStateOf<KinshipTermSpec?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickedRelative by remember { mutableStateOf<PersonEntity?>(null) }
    var newRelativeGender by remember { mutableStateOf(Gender.UNSPECIFIED) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val spec = selectedSpec
    val candidates = state.people
        .filter { it.id != person.id }
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
    val exactMatch = candidates.firstOrNull { it.name == query.trim() }
    val canCreate = query.isNotBlank() && exactMatch == null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(if (spec == null) "给「${person.name}」添加亲人" else "${person.name} 是对方的${spec.term}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (spec == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KinshipGroup.entries.forEach { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = { selectedGroup = group },
                                label = { Text(group.title) },
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KinshipTermCatalog.panelGroups
                            .first { it.first == selectedGroup }
                            .second
                            .forEach { entry ->
                                FilterChip(
                                    selected = false,
                                    onClick = { selectedSpec = entry },
                                    label = { Text(entry.term) },
                                )
                            }
                    }
                } else {
                    Text(
                        text = "${person.name} 是 对方 的${spec.term}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pickedRelative == null) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it; error = null },
                            label = { Text("搜索或输入新人物姓名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            if (canCreate) {
                                item(key = "create") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                pickedRelative = null
                                                error = null
                                            }
                                            .padding(vertical = 10.dp),
                                    ) {
                                        Text("新建人物：${query.trim()}")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Gender.entries.forEach { gender ->
                                                FilterChip(
                                                    selected = newRelativeGender == gender,
                                                    onClick = { newRelativeGender = gender },
                                                    label = {
                                                        Text(
                                                            when (gender) {
                                                                Gender.MALE -> "男"
                                                                Gender.FEMALE -> "女"
                                                                Gender.UNSPECIFIED -> "未知"
                                                            },
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            items(candidates, key = { it.id }) { candidate ->
                                Text(
                                    text = candidate.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pickedRelative = candidate
                                            query = candidate.name
                                        }
                                        .padding(vertical = 10.dp),
                                )
                            }
                        }
                    } else {
                        val relative = pickedRelative!!
                        Text(
                            text = "对方：${relative.name}（已存在）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when {
                spec == null -> {}
                pickedRelative == null && !canCreate -> {}
                else -> {
                    TextButton(
                        onClick = {
                            scope.launch {
                                saving = true
                                val result = viewModel.commitKinshipEntry(
                                    subjectName = person.name,
                                    relativeName = query.trim(),
                                    spec = spec,
                                    subjectPersonId = person.id,
                                    relativePersonId = pickedRelative?.id,
                                    relativeGender = newRelativeGender,
                                )
                                saving = false
                                if (result == null) {
                                    onDismiss()
                                } else {
                                    error = result
                                }
                            }
                        },
                        enabled = !saving && query.isNotBlank(),
                    ) {
                        Text(if (saving) "正在保存…" else "确认写入")
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (spec != null) {
                    TextButton(
                        onClick = {
                            selectedSpec = null
                            pickedRelative = null
                            query = ""
                            error = null
                        },
                        enabled = !saving,
                    ) { Text("重新选称谓") }
                }
                TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
            }
        },
    )
}

@Composable
private fun KinshipPersonResolutionRow(
    name: String,
    person: PersonEntity?,
    gender: Gender,
    onGenderChange: (Gender) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (person != null) {
            PersonAvatar(person = person, size = 40.dp)
            Column {
                Text(person.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "库中已存在",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "将新建：$name",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gender.entries.forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { onGenderChange(option) },
                            label = {
                                Text(
                                    when (option) {
                                        Gender.MALE -> "男"
                                        Gender.FEMALE -> "女"
                                        Gender.UNSPECIFIED -> "未知"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
