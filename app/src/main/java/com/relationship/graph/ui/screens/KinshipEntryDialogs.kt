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
import androidx.compose.material3.RadioButton
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
import com.relationship.graph.data.kinship.KinshipCommitResult
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
internal fun kinshipPersonsFor(state: AppUiState, name: String): List<PersonEntity> {
    val trimmed = name.trim()
    if (trimmed == "我" || trimmed == "自己") {
        return state.myPersonId
            ?.let { id -> state.people.firstOrNull { it.id == id } }
            ?.let { listOf(it) }
            ?: emptyList()
    }
    return state.people.filter { it.name == trimmed }
}

/**
 * 一句话录入的预览确认弹窗：解析结果一目了然；库中恰好一位同名时自动绑定，
 * 多位同名时内联列出供选择，避免静默绑错人；库里没有的人物可补建（姓名+性别）。
 */
@Composable
fun KinshipSentenceDialog(
    parsed: KinshipParseResult.Success,
    state: AppUiState,
    viewModel: RelationshipViewModel,
    onDismiss: () -> Unit,
    onEditRelationship: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var subjectGender by remember { mutableStateOf(Gender.UNSPECIFIED) }
    var relativeGender by remember { mutableStateOf(Gender.UNSPECIFIED) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var duplicateId by remember { mutableStateOf<String?>(null) }

    val subjectOptions = remember(parsed) { kinshipPersonsFor(state, parsed.subjectName) }
    val relativeOptions = remember(parsed) { kinshipPersonsFor(state, parsed.relativeName) }
    var subjectChoice by remember(parsed) {
        mutableStateOf(initialChoiceOf(subjectOptions))
    }
    var relativeChoice by remember(parsed) {
        mutableStateOf(initialChoiceOf(relativeOptions))
    }

    val ready = subjectChoice != null && relativeChoice != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("确认添加关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${parsed.subjectName} 是 ${parsed.relativeName} 的${parsed.spec.term}",
                    style = MaterialTheme.typography.titleMedium,
                )
                KinshipPersonOptionsRow(
                    label = parsed.subjectName,
                    options = subjectOptions,
                    choice = subjectChoice,
                    onChoice = { subjectChoice = it },
                    gender = subjectGender,
                    onGenderChange = { subjectGender = it },
                )
                KinshipPersonOptionsRow(
                    label = parsed.relativeName,
                    options = relativeOptions,
                    choice = relativeChoice,
                    onChoice = { relativeChoice = it },
                    gender = relativeGender,
                    onGenderChange = { relativeGender = it },
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        duplicateId = null
                        val result = viewModel.commitKinshipEntry(
                            subjectName = parsed.subjectName,
                            relativeName = parsed.relativeName,
                            spec = parsed.spec,
                            subjectPersonId = chosenPersonId(subjectChoice, subjectOptions),
                            relativePersonId = chosenPersonId(relativeChoice, relativeOptions),
                            subjectGender = subjectGender,
                            relativeGender = relativeGender,
                        )
                        saving = false
                        when (result) {
                            is KinshipCommitResult.Success -> onDismiss()
                            is KinshipCommitResult.Duplicate -> {
                                error = result.message
                                duplicateId = result.relationshipId
                            }
                            is KinshipCommitResult.Failure -> error = result.message
                        }
                    }
                },
                enabled = ready && !saving,
            ) {
                Text(if (saving) "正在保存…" else "确认写入")
            }
        },
        dismissButton = {
            Row {
                duplicateId?.let { id ->
                    TextButton(
                        onClick = {
                            duplicateId = null
                            onDismiss()
                            onEditRelationship(id)
                        },
                        enabled = !saving,
                    ) { Text("去编辑已有关系") }
                }
                TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
            }
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
    onEditRelationship: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(KinshipGroup.DIRECT) }
    var selectedSpec by remember { mutableStateOf<KinshipTermSpec?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickedRelative by remember { mutableStateOf<PersonEntity?>(null) }
    var newRelativeGender by remember { mutableStateOf(Gender.UNSPECIFIED) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var duplicateId by remember { mutableStateOf<String?>(null) }

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
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                                error = null
                                duplicateId = null
                                val result = viewModel.commitKinshipEntry(
                                    subjectName = person.name,
                                    relativeName = query.trim(),
                                    spec = spec,
                                    subjectPersonId = person.id,
                                    relativePersonId = pickedRelative?.id,
                                    relativeGender = newRelativeGender,
                                )
                                saving = false
                                when (result) {
                                    is KinshipCommitResult.Success -> onDismiss()
                                    is KinshipCommitResult.Duplicate -> {
                                        error = result.message
                                        duplicateId = result.relationshipId
                                    }
                                    is KinshipCommitResult.Failure -> error = result.message
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
                duplicateId?.let { id ->
                    TextButton(
                        onClick = {
                            duplicateId = null
                            onDismiss()
                            onEditRelationship(id)
                        },
                        enabled = !saving,
                    ) { Text("去编辑已有关系") }
                }
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

private const val CREATE_NEW = "__create__"

private fun initialChoiceOf(options: List<PersonEntity>): String? = when (options.size) {
    1 -> options[0].id
    0 -> CREATE_NEW
    else -> null
}

private fun chosenPersonId(choice: String?, options: List<PersonEntity>): String? =
    if (choice == null || choice == CREATE_NEW) {
        null
    } else {
        options.firstOrNull { it.id == choice }?.id
    }

@Composable
private fun KinshipPersonOptionsRow(
    label: String,
    options: List<PersonEntity>,
    choice: String?,
    onChoice: (String?) -> Unit,
    gender: Gender,
    onGenderChange: (Gender) -> Unit,
) {
    when {
        options.size >= 2 -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "匹配到 ${options.size} 位同名「$label」，请选择：",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChoice(option.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = choice == option.id,
                        onClick = { onChoice(option.id) },
                    )
                    PersonAvatar(person = option, size = 34.dp)
                    Text(
                        text = option.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    if (option.phone.isNotBlank()) {
                        Text(
                            text = option.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        options.size == 1 -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PersonAvatar(person = options[0], size = 40.dp)
            Column {
                Text(options[0].name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "库中已存在",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "将新建：$label",
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
