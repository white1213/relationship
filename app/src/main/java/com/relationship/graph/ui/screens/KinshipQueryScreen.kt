package com.relationship.graph.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.relationship.graph.data.inference.InferenceConfidence
import com.relationship.graph.data.inference.KinshipQueryEngine
import com.relationship.graph.data.inference.KinshipQueryResult
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState
import com.relationship.graph.ui.components.PersonAvatar

@Composable
fun KinshipQueryScreen(state: AppUiState) {
    var referencePersonId by rememberSaveable { mutableStateOf(state.myPersonId) }
    var targetPersonId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.people, state.myPersonId) {
        if (referencePersonId == null || state.person(referencePersonId) == null) {
            referencePersonId = state.myPersonId ?: state.people.firstOrNull()?.id
        }
        if (targetPersonId == null || state.person(targetPersonId) == null) {
            targetPersonId = state.people.firstOrNull { it.id != referencePersonId }?.id
        }
    }

    val referencePerson = state.person(referencePersonId)
    val targetPerson = state.person(targetPersonId)
    val result = remember(
        referencePersonId,
        targetPersonId,
        state.relationships,
        state.relationTypes,
        state.inferredCandidates,
    ) {
        if (referencePersonId != null && targetPersonId != null) {
            KinshipQueryEngine.query(
                referencePersonId = referencePersonId!!,
                targetPersonId = targetPersonId!!,
                relationships = state.relationships,
                relationTypes = state.relationTypes,
                inferredCandidates = state.inferredCandidates,
                people = state.people,
                ageOrders = state.relativeAgeOrders,
            )
        } else {
            null
        }
    }

    Scaffold(topBar = { AppTopBar(title = "称谓查询") }) { padding ->
        if (state.people.size < 2) {
            EmptyState(
                title = "至少需要两个人物",
                detail = "添加人物和家庭关系后，可以查询双方称谓。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            val horizontal = maxWidth >= 700.dp
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (horizontal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonSelectionCard(
                            label = "当前人物",
                            person = referencePerson,
                            people = state.people,
                            excludedPersonId = targetPersonId,
                            onSelect = { referencePersonId = it },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val previous = referencePersonId
                                referencePersonId = targetPersonId
                                targetPersonId = previous
                            },
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "交换人物")
                        }
                        PersonSelectionCard(
                            label = "查询对象",
                            person = targetPerson,
                            people = state.people,
                            excludedPersonId = referencePersonId,
                            onSelect = { targetPersonId = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    PersonSelectionCard(
                        label = "当前人物",
                        person = referencePerson,
                        people = state.people,
                        excludedPersonId = targetPersonId,
                        onSelect = { referencePersonId = it },
                    )
                    OutlinedButton(
                        onClick = {
                            val previous = referencePersonId
                            referencePersonId = targetPersonId
                            targetPersonId = previous
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Text("交换人物")
                    }
                    PersonSelectionCard(
                        label = "查询对象",
                        person = targetPerson,
                        people = state.people,
                        excludedPersonId = referencePersonId,
                        onSelect = { targetPersonId = it },
                    )
                }
                KinshipResultCard(
                    reference = referencePerson,
                    target = targetPerson,
                    result = result,
                )
            }
        }
    }
}

@Composable
private fun PersonSelectionCard(
    label: String,
    person: PersonEntity?,
    people: List<PersonEntity>,
    excludedPersonId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (person != null) {
                PersonAvatar(person = person, size = 48.dp)
            } else {
                Icon(Icons.Rounded.PersonSearch, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = person?.name ?: "选择人物",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    if (showPicker) {
        PersonQueryPickerDialog(
            people = people.filter { it.id != excludedPersonId },
            onSelect = {
                onSelect(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun PersonQueryPickerDialog(
    people: List<PersonEntity>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = people.filter { it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择人物") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(filtered, key = { it.id }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(person.id) }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PersonAvatar(person = person, size = 38.dp)
                            Text(person.name)
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

@Composable
private fun KinshipResultCard(
    reference: PersonEntity?,
    target: PersonEntity?,
    result: KinshipQueryResult?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (reference == null || target == null) {
                Text("请选择两个人物")
            } else if (result == null) {
                Text("暂未找到可推导的称谓")
                Text(
                    text = "请补充父母、配偶或兄弟姐妹关系后重试。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = "${reference.name} 称呼 ${target.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = result.referenceCallsTarget,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${target.name} 称呼 ${reference.name}：${result.targetCallsReference}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (result.isDirect) {
                        "来源：${result.explanation}"
                    } else {
                        "推导依据：${result.explanation}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                result.confidence?.let {
                    Text(
                        text = it.displayName(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun InferenceConfidence.displayName(): String = when (this) {
    InferenceConfidence.HIGH -> "可信度高"
    InferenceConfidence.MEDIUM_HIGH -> "可信度中高"
    InferenceConfidence.MEDIUM -> "可信度中等"
}
