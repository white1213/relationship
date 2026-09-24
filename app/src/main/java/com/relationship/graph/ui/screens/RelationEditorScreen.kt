package com.relationship.graph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState

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
        mutableStateOf(existing?.relationTypeId ?: state.relationTypes.firstOrNull()?.id.orEmpty())
    }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var forwardFromFirst by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.fromPersonId != secondPersonId)
    }
    var firstMenu by remember { mutableStateOf(false) }
    var secondMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var customTypeDialog by remember { mutableStateOf(false) }

    val firstPerson = state.person(firstPersonId)
    val secondPerson = state.person(secondPersonId)
    val relationType = state.relationType(relationTypeId)

    LaunchedEffect(state.relationTypes) {
        if (relationTypeId.isBlank()) {
            state.relationTypes.firstOrNull()?.let { relationTypeId = it.id }
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
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { typeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = relationType?.name ?: "选择关系类型",
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = typeMenu,
                        onDismissRequest = { typeMenu = false },
                    ) {
                        state.relationTypes.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (type.direction == RelationDirection.DIRECTED) {
                                            "${type.name} / ${type.inverseName.orEmpty()}"
                                        } else {
                                            type.name
                                        },
                                    )
                                },
                                onClick = {
                                    relationTypeId = type.id
                                    typeMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("新增自定义关系") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = {
                                typeMenu = false
                                customTypeDialog = true
                            },
                        )
                    }
                }
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

    if (customTypeDialog) {
        CustomRelationTypeDialog(
            onDismiss = { customTypeDialog = false },
            onSave = { name, inverseName, direction ->
                viewModel.createCustomRelationType(
                    name = name,
                    inverseName = inverseName,
                    category = RelationCategory.CUSTOM,
                    direction = direction,
                )
                customTypeDialog = false
            },
        )
    }
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
