package com.relationship.graph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState
import com.relationship.graph.ui.graph.GraphCanvas
import com.relationship.graph.ui.graph.GraphEdgeGroup
import com.relationship.graph.ui.graph.buildEdgeGroups
import com.relationship.graph.ui.relationshipSentence

@Composable
fun GraphScreen(
    state: AppUiState,
    viewModel: RelationshipViewModel,
    onPersonClick: (String) -> Unit,
    onAddPerson: () -> Unit,
    onAddRelationship: () -> Unit,
    onEditRelationship: (String, String) -> Unit,
) {
    var searchVisible by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var edgeDialog by remember { mutableStateOf<GraphEdgeGroup?>(null) }
    var relationshipToDelete by remember { mutableStateOf<RelationshipEntity?>(null) }

    val edgeGroups = remember(state.relationships, state.relationTypes) {
        buildEdgeGroups(state.relationships, state.relationTypes)
    }
    val highlightedPeople = highlightedPersonIds(state)
    val highlightedEdges = highlightedEdgeKeys(state, edgeGroups)

    Scaffold(
        topBar = {
            AppTopBar(
                title = "关系图谱",
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchVisible) "关闭搜索" else "搜索",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenuExpanded = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加")
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("添加人物") },
                        leadingIcon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            onAddPerson()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("添加关系") },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            onAddRelationship()
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text("搜索姓名、电话或标签") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text("全部") },
                    )
                }
                items(RelationCategory.entries) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = {
                            viewModel.setCategory(
                                if (state.selectedCategory == category) null else category,
                            )
                        },
                        label = { Text(category.displayName()) },
                    )
                }
            }

            if (state.people.isEmpty()) {
                EmptyState(
                    title = "还没有人物",
                    detail = "点击右下角按钮添加第一位人物。",
                    modifier = Modifier.weight(1f),
                )
            } else {
                GraphCanvas(
                    people = state.people,
                    edgeGroups = edgeGroups,
                    highlightedPersonIds = highlightedPeople,
                    highlightedEdgeKeys = highlightedEdges,
                    onPersonClick = onPersonClick,
                    onEdgeAction = { edgeDialog = it },
                    onPositionsCommitted = viewModel::saveGraphPositions,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    edgeDialog?.let { group ->
        EdgeRelationshipDialog(
            group = group,
            state = state,
            onDismiss = { edgeDialog = null },
            onAdd = {
                edgeDialog = null
                onAddRelationship()
            },
            onEdit = { relationshipId ->
                edgeDialog = null
                onEditRelationship(relationshipId, group.firstPersonId)
            },
            onDelete = { relationship ->
                edgeDialog = null
                relationshipToDelete = relationship
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
                        viewModel.deleteRelationship(relationship)
                        relationshipToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { relationshipToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun EdgeRelationshipDialog(
    group: GraphEdgeGroup,
    state: AppUiState,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (RelationshipEntity) -> Unit,
) {
    val first = state.person(group.firstPersonId)
    val second = state.person(group.secondPersonId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${first?.name.orEmpty()} 与 ${second?.name.orEmpty()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                group.relationships.forEach { relationship ->
                    val type = state.relationType(relationship.relationTypeId)
                    if (type != null) {
                        Column {
                            Text(
                                text = relationshipSentence(
                                    relationship = relationship,
                                    type = type,
                                    fromName = state.person(relationship.fromPersonId)?.name.orEmpty(),
                                    toName = state.person(relationship.toPersonId)?.name.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            relationship.note.takeIf(String::isNotBlank)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { onEdit(relationship.id) }) { Text("编辑") }
                                TextButton(onClick = { onDelete(relationship) }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) { Text("添加关系") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun highlightedPersonIds(state: AppUiState): Set<String>? {
    if (state.searchQuery.isBlank() && state.selectedCategory == null) return null
    val query = state.searchQuery.trim().lowercase()
    val tagsByName = state.tagsByPerson
    val queryMatches = if (query.isBlank()) {
        state.people.map { it.id }.toSet()
    } else {
        state.people.filter { person ->
            person.name.lowercase().contains(query) ||
                person.phone.lowercase().contains(query) ||
                tagsByName[person.id].orEmpty().any { it.name.lowercase().contains(query) }
        }.map { it.id }.toSet()
    }
    val categoryMatches = state.selectedCategory?.let { category ->
        val categoryTypeIds = state.relationTypes
            .filter { it.category == category }
            .map { it.id }
            .toSet()
        state.relationships
            .filter { it.relationTypeId in categoryTypeIds }
            .flatMap { listOf(it.fromPersonId, it.toPersonId) }
            .toSet()
    } ?: state.people.map { it.id }.toSet()
    return queryMatches intersect categoryMatches
}

private fun highlightedEdgeKeys(
    state: AppUiState,
    groups: List<GraphEdgeGroup>,
): Set<String>? {
    if (state.searchQuery.isBlank() && state.selectedCategory == null) return null
    val queryMatches = highlightedPersonIds(state) ?: return null
    val categoryTypeIds = state.selectedCategory?.let { category ->
        state.relationTypes.filter { it.category == category }.map { it.id }.toSet()
    }
    return groups.filter { group ->
        (group.firstPersonId in queryMatches || group.secondPersonId in queryMatches) &&
            (categoryTypeIds == null || group.relationTypes.any { it.id in categoryTypeIds })
    }.map { it.key }.toSet()
}

private fun RelationCategory.displayName(): String = when (this) {
    RelationCategory.FAMILY -> "家庭"
    RelationCategory.SOCIAL -> "社交"
    RelationCategory.CUSTOM -> "自定义"
}
