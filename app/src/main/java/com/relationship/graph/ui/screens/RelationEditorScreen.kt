package com.relationship.graph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.MarriageKinshipMode
import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun RelationEditorScreen(
    state: AppUiState,
    relationshipId: String?,
    initialPersonId: String?,
    viewModel: RelationshipViewModel,
    onBack: () -> Unit,
) {
    val existing = state.relationship(relationshipId)
    val defaultFirst = existing?.fromPersonId ?: initialPersonId ?: state.people.firstOrNull()?.id
    val defaultSecond = existing?.toPersonId
        ?: state.people.firstOrNull { it.id != defaultFirst }?.id

    var firstPersonId by rememberSaveable(existing?.id, defaultFirst) {
        mutableStateOf(defaultFirst.orEmpty())
    }
    var secondPersonId by rememberSaveable(existing?.id, defaultSecond) {
        mutableStateOf(defaultSecond.orEmpty())
    }
    var relationTypeId by rememberSaveable(existing?.id) {
        mutableStateOf(
            existing?.relationTypeId
                ?: state.relationTypes.firstOrNull { !it.isInferenceOnly }?.id.orEmpty(),
        )
    }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var marriageKinshipMode by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.marriageKinshipMode ?: MarriageKinshipMode.RESPECTIVE)
    }
    var forwardFromFirst by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.fromPersonId != secondPersonId)
    }
    var firstMenu by remember { mutableStateOf(false) }
    var secondMenu by remember { mutableStateOf(false) }
    var typePickerVisible by remember { mutableStateOf(false) }
    var customTypeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val firstPerson = state.person(firstPersonId)
    val secondPerson = state.person(secondPersonId)
    val relationType = state.relationType(relationTypeId)
    val selectableRelationTypes = state.relationTypes.filter {
        !it.isInferenceOnly || it.id == existing?.relationTypeId
    }

    LaunchedEffect(selectableRelationTypes) {
        if (relationTypeId.isBlank()) {
            selectableRelationTypes.firstOrNull()?.let { relationTypeId = it.id }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (existing == null) "添加关系" else "编辑关系",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.people.size < 2) {
            EmptyState(
                title = "至少需要两个人物",
                detail = "先添加人物，再建立关系。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("人物", style = MaterialTheme.typography.titleMedium)
            PersonPicker(
                label = "人物一",
                selectedName = firstPerson?.name.orEmpty(),
                people = state.people,
                expanded = firstMenu,
                onExpandedChange = { firstMenu = it },
                onSelect = { selected ->
                    firstPersonId = selected
                    if (secondPersonId == selected) {
                        secondPersonId = state.people.firstOrNull { it.id != selected }?.id.orEmpty()
                    }
                },
            )
            PersonPicker(
                label = "人物二",
                selectedName = secondPerson?.name.orEmpty(),
                people = state.people,
                expanded = secondMenu,
                onExpandedChange = { secondMenu = it },
                onSelect = { selected ->
                    secondPersonId = selected
                    if (firstPersonId == selected) {
                        firstPersonId = state.people.firstOrNull { it.id != selected }?.id.orEmpty()
                    }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关系", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { typePickerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = relationType?.name ?: "选择关系类型",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${selectableRelationTypes.size} 种",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
                Text(
                    text = "关系类型较多时可搜索选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (relationType?.direction == RelationDirection.DIRECTED &&
                firstPerson != null &&
                secondPerson != null
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关系方向", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = forwardFromFirst,
                        onClick = { forwardFromFirst = true },
                        label = {
                            Text(
                                "${firstPerson.name} 是 ${secondPerson.name} 的${relationType.name}",
                            )
                        },
                    )
                    FilterChip(
                        selected = !forwardFromFirst,
                        onClick = { forwardFromFirst = false },
                        label = {
                            Text(
                                "${secondPerson.name} 是 ${firstPerson.name} 的${relationType.name}",
                            )
                        },
                    )
                }
            }

            if (relationType?.let(RelationshipSemantics::kind) == FamilyRelationKind.SPOUSE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("婚后称谓", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MarriageKinshipMode.entries.forEach { mode ->
                            FilterChip(
                                selected = marriageKinshipMode == mode,
                                onClick = { marriageKinshipMode = mode },
                                label = {
                                    Text(
                                        when (mode) {
                                            MarriageKinshipMode.FOLLOW_HUSBAND -> "随夫家"
                                            MarriageKinshipMode.FOLLOW_WIFE -> "随妻家"
                                            MarriageKinshipMode.RESPECTIVE -> "各自称呼"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = "用于生成双方亲属的日常称谓，不修改已录入的血亲关系。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("关系备注") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val type = relationType ?: return@Button
                    val fromPersonId: String
                    val toPersonId: String
                    if (type.direction == RelationDirection.DIRECTED && !forwardFromFirst) {
                        fromPersonId = secondPersonId
                        toPersonId = firstPersonId
                    } else {
                        fromPersonId = firstPersonId
                        toPersonId = secondPersonId
                    }
                    viewModel.saveRelationship(
                        relationshipId = relationshipId,
                        fromPersonId = fromPersonId,
                        toPersonId = toPersonId,
                        relationTypeId = type.id,
                        note = note,
                        marriageKinshipMode = marriageKinshipMode,
                        existing = existing,
                    )
                    onBack()
                },
                enabled = firstPersonId.isNotBlank() &&
                    secondPersonId.isNotBlank() &&
                    firstPersonId != secondPersonId &&
                    relationTypeId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (typePickerVisible) {
        RelationTypePickerDialog(
            relationTypes = selectableRelationTypes,
            onSelect = { type ->
                relationTypeId = type.id
                if (RelationshipSemantics.kind(type) == FamilyRelationKind.SPOUSE) {
                    marriageKinshipMode = when {
                        firstPerson?.gender == com.relationship.graph.data.local.Gender.MALE &&
                            secondPerson?.gender == com.relationship.graph.data.local.Gender.FEMALE ->
                            MarriageKinshipMode.FOLLOW_HUSBAND
                        firstPerson?.gender == com.relationship.graph.data.local.Gender.FEMALE &&
                            secondPerson?.gender == com.relationship.graph.data.local.Gender.MALE ->
                            MarriageKinshipMode.FOLLOW_HUSBAND
                        else -> MarriageKinshipMode.RESPECTIVE
                    }
                }
                typePickerVisible = false
            },
            onAddCustom = {
                typePickerVisible = false
                customTypeDialog = true
            },
            onDismiss = { typePickerVisible = false },
        )
    }

    if (customTypeDialog) {
        CustomRelationTypeDialog(
            onDismiss = { customTypeDialog = false },
            onSave = { name, inverseName, direction ->
                scope.launch {
                    val createdId = viewModel.createCustomRelationType(
                        name = name,
                        inverseName = inverseName,
                        category = RelationCategory.CUSTOM,
                        direction = direction,
                    )
                    if (createdId != null) {
                        relationTypeId = createdId
                        customTypeDialog = false
                    }
                }
            },
        )
    }
}

@Composable
private fun RelationTypePickerDialog(
    relationTypes: List<com.relationship.graph.data.local.RelationTypeEntity>,
    onSelect: (com.relationship.graph.data.local.RelationTypeEntity) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val filteredTypes = if (normalizedQuery.isBlank()) {
        relationTypes
    } else {
        relationTypes.filter {
            it.name.lowercase().contains(normalizedQuery) ||
                it.inverseName.orEmpty().lowercase().contains(normalizedQuery) ||
                searchAliases(it.id).any { alias -> alias.contains(normalizedQuery) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择关系类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索关系类型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = onAddCustom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("新增自定义关系")
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(filteredTypes, key = { it.id }) { type ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(type) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = if (type.direction == RelationDirection.DIRECTED) {
                                    "${type.name} / ${type.inverseName.orEmpty()}"
                                } else {
                                    type.name
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (filteredTypes.isEmpty()) {
                        item {
                            Text(
                                text = "没有匹配的关系类型",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun searchAliases(relationTypeId: String): List<String> = when (relationTypeId) {
    "preset_biao_cousin" -> listOf("舅舅家子女", "姑姑家子女", "姨妈家子女", "表兄弟", "表姐妹")
    "preset_tang_cousin" -> listOf("叔叔家子女", "伯父家子女", "大爷家子女", "堂兄弟", "堂姐妹")
    else -> emptyList()
}

@Composable
private fun PersonPicker(
    label: String,
    selectedName: String,
    people: List<com.relationship.graph.data.local.PersonEntity>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.layout.Box {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (selectedName.isBlank()) label else selectedName,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.name) },
                    onClick = {
                        onSelect(person.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomRelationTypeDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, inverseName: String?, direction: RelationDirection) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var inverseName by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(RelationDirection.BIDIRECTIONAL) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("新增自定义关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("关系名称") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == RelationDirection.BIDIRECTIONAL,
                        onClick = { direction = RelationDirection.BIDIRECTIONAL },
                        label = { Text("双向") },
                    )
                    FilterChip(
                        selected = direction == RelationDirection.DIRECTED,
                        onClick = { direction = RelationDirection.DIRECTED },
                        label = { Text("定向") },
                    )
                }
                if (direction == RelationDirection.DIRECTED) {
                    OutlinedTextField(
                        value = inverseName,
                        onValueChange = { inverseName = it; error = null },
                        label = { Text("反向关系名称") },
                        placeholder = { Text("例如：子女") },
                        singleLine = true,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        name.isBlank() -> "请输入关系名称"
                        direction == RelationDirection.DIRECTED && inverseName.isBlank() ->
                            "请输入反向关系名称"
                        else -> null
                    }
                    if (error == null) {
                        onSave(name, inverseName.ifBlank { null }, direction)
                    }
                },
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
