package com.relationship.graph.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.inference.InferredRelationshipCandidate
import com.relationship.graph.data.inference.InferenceConfidence
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState
import com.relationship.graph.ui.components.MyPersonPickerDialog
import com.relationship.graph.ui.components.PersonAvatar
import com.relationship.graph.ui.graph.GraphCanvas
import com.relationship.graph.ui.graph.GraphEdgeGroup
import com.relationship.graph.ui.graph.GraphFocusEngine
import com.relationship.graph.ui.graph.GraphFocusScope
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
    var myPersonDialogDismissed by rememberSaveable { mutableStateOf(false) }
    var showMyPersonDialog by rememberSaveable { mutableStateOf(false) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusScope by rememberSaveable { mutableStateOf<GraphFocusScope?>(null) }
    var centerOnPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var branchDialogPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var inferenceCandidateDialog by remember { mutableStateOf<InferredRelationshipCandidate?>(null) }
    var showLegend by rememberSaveable { mutableStateOf(false) }

    val modeRelationships = remember(
        state.relationships,
        state.relationTypes,
        state.graphMode,
    ) {
        filterRelationshipsForMode(state)
    }
    val modePeople = remember(state.people, modeRelationships, state.myPersonId, state.graphMode) {
        filterPeopleForMode(state, modeRelationships)
    }
    val focusResult = remember(focusPersonId, focusScope, modeRelationships, state.relationTypes) {
        focusPersonId?.let { personId ->
            GraphFocusEngine.resolve(
                anchorPersonId = personId,
                scope = focusScope ?: GraphFocusScope.RELATED,
                relationships = modeRelationships,
                relationTypes = state.relationTypes,
            )
        }
    }
    val focusedPersonIds = focusResult?.personIds
    val visibleRelationships = remember(modeRelationships, focusedPersonIds, focusScope) {
        if (focusScope == null || focusedPersonIds == null) {
            modeRelationships
        } else {
            modeRelationships.filter {
                it.fromPersonId in focusedPersonIds && it.toPersonId in focusedPersonIds
            }
        }
    }
    val visiblePeople = remember(modePeople, focusedPersonIds, focusScope) {
        if (focusScope == null || focusedPersonIds == null) {
            modePeople
        } else {
            modePeople.filter { it.id in focusedPersonIds }
        }
    }
    val edgeGroups = remember(visibleRelationships, state.relationTypes) {
        buildEdgeGroups(visibleRelationships, state.relationTypes)
    }
    val searchHighlightedPeople = highlightedPersonIds(state, visiblePeople, visibleRelationships)
    val searchHighlightedEdges = highlightedEdgeKeys(
        state = state,
        people = visiblePeople,
        relationships = visibleRelationships,
        groups = edgeGroups,
    )
    val focusedRelationshipIds = focusResult?.relationshipIds.orEmpty()
    val focusedEdgeKeys = edgeGroups
        .filter { group ->
            group.relationships.any { it.id in focusedRelationshipIds }
        }
        .map { it.key }
        .toSet()
    val highlightedPeople = when {
        focusScope != null -> null
        focusPersonId == null -> searchHighlightedPeople
        searchHighlightedPeople == null -> focusedPersonIds
        else -> searchHighlightedPeople intersect focusedPersonIds.orEmpty()
    }
    val highlightedEdges = when {
        focusScope != null -> null
        focusPersonId == null -> searchHighlightedEdges
        searchHighlightedEdges == null -> focusedEdgeKeys
        else -> searchHighlightedEdges intersect focusedEdgeKeys
    }

    LaunchedEffect(state.searchQuery, visiblePeople, state.graphMode) {
        val query = state.searchQuery.trim().lowercase()
        if (query.isNotEmpty()) {
            val matches = visiblePeople.filter { person ->
                person.name.lowercase().contains(query) ||
                    person.phone.lowercase().contains(query) ||
                    state.tagsByPerson[person.id].orEmpty().any {
                        it.name.lowercase().contains(query)
                    }
            }
            if (matches.size == 1) {
                val personId = matches.single().id
                selectedPersonId = personId
                focusPersonId = personId
                focusScope = null
                centerOnPersonId = personId
            }
        }
    }

    LaunchedEffect(state.isLoading, state.people.size, state.myPersonId, myPersonDialogDismissed) {
        if (!state.isLoading &&
            state.myPersonId == null &&
            state.people.isNotEmpty() &&
            !myPersonDialogDismissed
        ) {
            showMyPersonDialog = true
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "关系图谱",
                actions = {
                    IconButton(onClick = { showLegend = true }) {
                        Icon(Icons.Rounded.Info, contentDescription = "图例")
                    }
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

            TabRow(selectedTabIndex = state.graphMode.ordinal) {
                GraphMode.entries.forEach { mode ->
                    Tab(
                        selected = state.graphMode == mode,
                        onClick = {
                            selectedPersonId = null
                            focusPersonId = null
                            focusScope = null
                            centerOnPersonId = null
                            viewModel.setGraphMode(mode)
                        },
                        text = { Text(mode.displayName()) },
                    )
                }
            }

            if (state.graphMode == GraphMode.ALL) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
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
            }

            if (state.people.isEmpty() ||
                (state.graphMode != GraphMode.ALL && visibleRelationships.isEmpty())
            ) {
                EmptyState(
                    title = when {
                        state.people.isEmpty() -> "还没有人物"
                        state.graphMode == GraphMode.FAMILY -> "还没有家庭关系"
                        else -> "还没有社交关系"
                    },
                    detail = when {
                        state.people.isEmpty() -> "点击右下角按钮添加第一位人物。"
                        state.graphMode == GraphMode.FAMILY -> "添加父母、子女、配偶或兄弟姐妹后，会按辈分排列。"
                        else -> "添加朋友、同事、同学或邻居后，会以我的关系为中心展开。"
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    GraphCanvas(
                        people = visiblePeople,
                        edgeGroups = edgeGroups,
                        mode = state.graphMode,
                        myPersonId = state.myPersonId,
                        graphPositions = state.graphPositions,
                        inferenceCandidates = if (state.graphMode == GraphMode.SOCIAL) {
                            emptyList()
                        } else {
                            state.inferredCandidates
                        },
                        showInferenceSuggestions = state.showInferenceSuggestions,
                        highlightedPersonIds = highlightedPeople,
                        highlightedEdgeKeys = highlightedEdges,
                        selectedPersonId = selectedPersonId,
                        centerOnPersonId = centerOnPersonId,
                        centerRequestKey = focusPersonId?.let {
                            "$it:${focusScope?.name}:${visiblePeople.size}"
                        },
                        onPersonSelected = { personId ->
                            selectedPersonId = personId
                            focusPersonId = personId
                            focusScope = null
                        },
                        onPersonLongPress = { branchDialogPersonId = it },
                        onBackgroundClick = {
                            selectedPersonId = null
                            focusPersonId = null
                            focusScope = null
                            centerOnPersonId = null
                        },
                        onInferenceCandidateAction = {
                            inferenceCandidateDialog = it
                        },
                        onEdgeAction = {
                            selectedPersonId = null
                            edgeDialog = it
                        },
                        onPersonMoved = { personId, x, y ->
                            viewModel.saveGraphPosition(personId, x, y)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.person(selectedPersonId)?.let { selectedPerson ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(14.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            tonalElevation = 5.dp,
                            shadowElevation = 5.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PersonAvatar(person = selectedPerson, size = 46.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedPerson.name,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = "${state.relationshipsForPerson(selectedPerson.id).size} 条关系",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    androidx.compose.material3.Button(
                                        onClick = { onPersonClick(selectedPerson.id) },
                                    ) {
                                        Text("查看详情")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        onClick = {
                                            focusPersonId = selectedPerson.id
                                            focusScope = GraphFocusScope.RELATED
                                            centerOnPersonId = selectedPerson.id
                                        },
                                    ) {
                                        Text("只看相关")
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedPersonId = null
                                            focusPersonId = null
                                            focusScope = null
                                            centerOnPersonId = null
                                            viewModel.setSearchQuery("")
                                            viewModel.setCategory(null)
                                        },
                                    ) {
                                        Text("显示全部")
                                    }
                                }
                            }
                        }
                    }
                }
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

    if (showMyPersonDialog) {
        MyPersonPickerDialog(
            people = state.people,
            selectedPersonId = state.myPersonId,
            onSelect = { personId ->
                viewModel.setMyPerson(personId)
                showMyPersonDialog = false
            },
            onDismiss = {
                showMyPersonDialog = false
                myPersonDialogDismissed = true
            },
        )
    }

    branchDialogPersonId?.let { personId ->
        val person = state.person(personId)
        AlertDialog(
            onDismissRequest = { branchDialogPersonId = null },
            title = { Text("${person?.name.orEmpty()} 的关系范围") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            selectedPersonId = personId
                            focusPersonId = personId
                            focusScope = GraphFocusScope.RELATED
                            centerOnPersonId = personId
                            branchDialogPersonId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("只看直接相关")
                    }
                    if (state.graphMode != GraphMode.SOCIAL) {
                        TextButton(
                            onClick = {
                                selectedPersonId = personId
                                focusPersonId = personId
                                focusScope = GraphFocusScope.ANCESTORS
                                centerOnPersonId = personId
                                branchDialogPersonId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("查看祖先")
                        }
                        TextButton(
                            onClick = {
                                selectedPersonId = personId
                                focusPersonId = personId
                                focusScope = GraphFocusScope.DESCENDANTS
                                centerOnPersonId = personId
                                branchDialogPersonId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("查看后代")
                        }
                        TextButton(
                            onClick = {
                                selectedPersonId = personId
                                focusPersonId = personId
                                focusScope = GraphFocusScope.BRANCH
                                centerOnPersonId = personId
                                branchDialogPersonId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("只看整个分支")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { branchDialogPersonId = null }) { Text("取消") }
            },
        )
    }

    if (showLegend) {
        GraphLegendDialog(
            showInferenceSuggestions = state.showInferenceSuggestions,
            onShowInferenceSuggestionsChange = viewModel::setShowInferenceSuggestions,
            onDismiss = { showLegend = false },
        )
    }

    inferenceCandidateDialog?.let { candidate ->
        val perspectivePersonId = selectedPersonId ?: candidate.toPersonId
        val otherPersonId = if (candidate.fromPersonId == perspectivePersonId) {
            candidate.toPersonId
        } else {
            candidate.fromPersonId
        }
        val perspective = state.person(perspectivePersonId)
        val other = state.person(otherPersonId)
        AlertDialog(
            onDismissRequest = { inferenceCandidateDialog = null },
            title = {
                Text(
                    "${other?.name.orEmpty()} · ${candidate.labelFor(perspectivePersonId)}",
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${perspective?.name.orEmpty()} → ${candidate.reason} → " +
                            other?.name.orEmpty(),
                    )
                    Text(
                        text = candidate.confidence.displayName(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmInference(candidate)
                        inferenceCandidateDialog = null
                    },
                ) {
                    Text("添加关系")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.dismissInference(candidate)
                            inferenceCandidateDialog = null
                        },
                    ) {
                        Text("忽略")
                    }
                    TextButton(onClick = { inferenceCandidateDialog = null }) {
                        Text("取消")
                    }
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

private fun filterRelationshipsForMode(state: AppUiState): List<RelationshipEntity> {
    val category = when (state.graphMode) {
        GraphMode.FAMILY -> RelationCategory.FAMILY
        GraphMode.SOCIAL -> RelationCategory.SOCIAL
        GraphMode.ALL -> null
    } ?: return state.relationships
    val typeIds = state.relationTypes
        .filter { it.category == category }
        .map { it.id }
        .toSet()
    return state.relationships.filter { it.relationTypeId in typeIds }
}

private fun filterPeopleForMode(
    state: AppUiState,
    relationships: List<RelationshipEntity>,
): List<com.relationship.graph.data.local.PersonEntity> {
    if (state.graphMode == GraphMode.ALL) return state.people
    val personIds = relationships
        .flatMap { listOf(it.fromPersonId, it.toPersonId) }
        .toMutableSet()
    state.myPersonId?.let(personIds::add)
    return state.people.filter { it.id in personIds }
}

private fun highlightedPersonIds(
    state: AppUiState,
    people: List<com.relationship.graph.data.local.PersonEntity>,
    relationships: List<RelationshipEntity>,
): Set<String>? {
    if (state.searchQuery.isBlank() && state.selectedCategory == null) return null
    val query = state.searchQuery.trim().lowercase()
    val tagsByName = state.tagsByPerson
    val queryMatches = if (query.isBlank()) {
        people.map { it.id }.toSet()
    } else {
        people.filter { person ->
            person.name.lowercase().contains(query) ||
                person.phone.lowercase().contains(query) ||
                tagsByName[person.id].orEmpty().any { it.name.lowercase().contains(query) }
        }.map { it.id }.toSet()
    }
    val categoryMatches = state.selectedCategory
        ?.takeIf { state.graphMode == GraphMode.ALL }
        ?.let { category ->
        val categoryTypeIds = state.relationTypes
            .filter { it.category == category }
            .map { it.id }
            .toSet()
        relationships
            .filter { it.relationTypeId in categoryTypeIds }
            .flatMap { listOf(it.fromPersonId, it.toPersonId) }
            .toSet()
    } ?: people.map { it.id }.toSet()
    return queryMatches intersect categoryMatches
}

private fun highlightedEdgeKeys(
    state: AppUiState,
    people: List<com.relationship.graph.data.local.PersonEntity>,
    relationships: List<RelationshipEntity>,
    groups: List<GraphEdgeGroup>,
): Set<String>? {
    if (state.searchQuery.isBlank() && state.selectedCategory == null) return null
    val queryMatches = highlightedPersonIds(state, people, relationships) ?: return null
    val categoryTypeIds = state.selectedCategory
        ?.takeIf { state.graphMode == GraphMode.ALL }
        ?.let { category ->
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

private fun GraphMode.displayName(): String = when (this) {
    GraphMode.FAMILY -> "家谱"
    GraphMode.SOCIAL -> "社交"
    GraphMode.ALL -> "全部"
}

@Composable
private fun GraphLegendDialog(
    showInferenceSuggestions: Boolean,
    onShowInferenceSuggestionsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendNodeRow(
                    label = "家庭人物",
                    primaryColor = Color(0xFF3F7FDD),
                )
                LegendNodeRow(
                    label = "社交人物",
                    primaryColor = Color(0xFF2D8C7F),
                )
                LegendNodeRow(
                    label = "家庭与社交都有",
                    primaryColor = Color(0xFF3F7FDD),
                    secondaryColor = Color(0xFF2D8C7F),
                )
                LegendNodeRow(
                    label = "我",
                    primaryColor = Color(0xFF244D86),
                    isMyPerson = true,
                )
                LegendLineRow("父母 / 子女", Color(0xFF3F7FDD), doubleLine = false, dashed = false)
                LegendLineRow("配偶 / 伴侣", Color(0xFFD35F78), doubleLine = true, dashed = false)
                LegendLineRow("兄弟姐妹", Color(0xFF5A8FD6), doubleLine = false, dashed = true)
                LegendLineRow("社交关系", Color(0xFF7A8796), doubleLine = false, dashed = true)
                LegendLineRow("候选亲属", Color(0xFF8CA7C4), doubleLine = false, dashed = true)
                LegendLineRow(
                    "已确认推导关系",
                    Color(0xFF79A6D2),
                    doubleLine = false,
                    dashed = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("显示候选亲属")
                    Switch(
                        checked = showInferenceSuggestions,
                        onCheckedChange = onShowInferenceSuggestionsChange,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun InferenceConfidence.displayName(): String = when (this) {
    InferenceConfidence.HIGH -> "可信度高"
    InferenceConfidence.MEDIUM_HIGH -> "可信度中高"
    InferenceConfidence.MEDIUM -> "可信度中等"
}

@Composable
private fun LegendNodeRow(
    label: String,
    primaryColor: Color,
    secondaryColor: Color? = null,
    isMyPerson: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.size(38.dp, 28.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = 10f
            drawCircle(Color.White, radius + 2f, center)
            drawCircle(primaryColor, radius, center)
            secondaryColor?.let { secondary ->
                drawArc(
                    color = secondary,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 3f),
                )
            }
            if (isMyPerson) {
                drawCircle(Color(0xFFF0B84A), radius = 4f, center = center + Offset(7f, 7f))
            }
        }
        Text(label)
    }
}

@Composable
private fun LegendLineRow(
    label: String,
    color: Color,
    doubleLine: Boolean,
    dashed: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.size(38.dp, 28.dp)) {
            val start = Offset(3f, size.height / 2f)
            val end = Offset(size.width - 3f, size.height / 2f)
            val pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null
            if (doubleLine) {
                drawLine(color, start + Offset(0f, -2f), end + Offset(0f, -2f), 2f)
                drawLine(color, start + Offset(0f, 2f), end + Offset(0f, 2f), 2f)
            } else {
                drawLine(color, start, end, 2.5f, pathEffect = pathEffect)
            }
        }
        Text(label)
    }
}
