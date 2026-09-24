package com.relationship.graph.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.inference.InferenceConfidence
import com.relationship.graph.data.inference.InferredRelationshipCandidate
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState
import com.relationship.graph.ui.components.PersonAvatar
import com.relationship.graph.ui.relationshipLabelForPerson

@Composable
fun PersonDetailScreen(
    state: AppUiState,
    personId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddRelationship: () -> Unit,
    onEditRelationship: (String) -> Unit,
    onConfirmInference: (InferredRelationshipCandidate) -> Unit,
    onDismissInference: (InferredRelationshipCandidate) -> Unit,
    onDeletePerson: (PersonEntity) -> Unit,
    onDeleteRelationship: (RelationshipEntity) -> Unit,
) {
    val person = state.person(personId)
    var showDeletePerson by remember { mutableStateOf(false) }
    var relationshipToDelete by remember { mutableStateOf<RelationshipEntity?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = person?.name ?: "人物详情",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (person != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (person == null) {
            EmptyState(
                title = "人物不存在",
                detail = "该人物可能已被删除。",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PersonAvatar(person = person, size = 86.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(person.name, style = MaterialTheme.typography.headlineSmall)
                    person.phone.takeIf(String::isNotBlank)?.let {
                        DetailText(label = "电话", value = it)
                    }
                    person.birthday.takeIf(String::isNotBlank)?.let {
                        DetailText(label = "生日", value = it)
                    }
                }
            }

            person.address.takeIf(String::isNotBlank)?.let {
                DetailText(label = "地址", value = it, modifier = Modifier.fillMaxWidth())
            }
            state.tagsByPerson[person.id].orEmpty().takeIf(List<*>::isNotEmpty)?.let { tags ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("标签", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = tags.joinToString("、") { it.name },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            person.notes.takeIf(String::isNotBlank)?.let {
                DetailText(label = "备注", value = it, modifier = Modifier.fillMaxWidth())
            }

            val inferredCandidates = state.inferenceCandidatesFor(person.id)
            if (inferredCandidates.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("可能的亲属关系", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${inferredCandidates.size} 条",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inferredCandidates.forEach { candidate ->
                    val otherPersonId = if (candidate.fromPersonId == person.id) {
                        candidate.toPersonId
                    } else {
                        candidate.fromPersonId
                    }
                    val otherPerson = state.person(otherPersonId)
                    if (otherPerson != null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PersonAvatar(person = otherPerson, size = 44.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = otherPerson.name,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = candidate.labelFor(person.id),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                    Text(
                                        text = candidate.confidence.displayName(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "推导依据：${candidate.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { onDismissInference(candidate) }) {
                                        Text("忽略")
                                    }
                                    OutlinedButton(
                                        onClick = { onConfirmInference(candidate) },
                                    ) {
                                        Text("添加关系")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("人际关系", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onAddRelationship) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("添加")
                }
            }

            val relationships = state.relationshipsForPerson(person.id)
            if (relationships.isEmpty()) {
                Text(
                    text = "还没有关系记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                relationships.forEach { relationship ->
                    val type = state.relationType(relationship.relationTypeId)
                    if (type != null) {
                        val otherId = if (relationship.fromPersonId == person.id) {
                            relationship.toPersonId
                        } else {
                            relationship.fromPersonId
                        }
                        val other = state.person(otherId)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditRelationship(relationship.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = other?.name ?: "已删除人物",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = relationshipLabelForPerson(
                                            relationship = relationship,
                                            type = type,
                                            personId = person.id,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    relationship.note.takeIf(String::isNotBlank)?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { relationshipToDelete = relationship }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "删除关系",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showDeletePerson = true },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("删除人物")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeletePerson) {
        val count = state.relationshipsForPerson(personId).size
        AlertDialog(
            onDismissRequest = { showDeletePerson = false },
            title = { Text("删除人物") },
            text = {
                Text(
                    if (count > 0) {
                        "删除后还会同时删除 $count 条关系，此操作无法撤销。"
                    } else {
                        "此操作无法撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePerson(requireNotNull(person))
                        showDeletePerson = false
                        onBack()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePerson = false }) { Text("取消") }
            },
        )
    }

    relationshipToDelete?.let { relationship ->
        AlertDialog(
            onDismissRequest = { relationshipToDelete = null },
            title = { Text("删除关系") },
            text = { Text("确定删除这条关系吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRelationship(relationship)
                        relationshipToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { relationshipToDelete = null }) { Text("取消") }
            },
        )
    }
}

private fun InferenceConfidence.displayName(): String = when (this) {
    InferenceConfidence.HIGH -> "可信度高"
    InferenceConfidence.MEDIUM_HIGH -> "可信度中高"
    InferenceConfidence.MEDIUM -> "可信度中等"
}

@Composable
private fun DetailText(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
