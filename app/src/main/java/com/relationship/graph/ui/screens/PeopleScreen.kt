package com.relationship.graph.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.EmptyState
import com.relationship.graph.ui.components.PersonAvatar

@Composable
fun PeopleScreen(
    state: AppUiState,
    viewModel: RelationshipViewModel,
    onPersonClick: (String) -> Unit,
    onAddPerson: () -> Unit,
) {
    val query = state.searchQuery.trim().lowercase()
    val filteredPeople = if (query.isBlank()) {
        state.people
    } else {
        state.people.filter { person ->
            person.name.lowercase().contains(query) ||
                person.phone.lowercase().contains(query) ||
                state.tagsByPerson[person.id].orEmpty().any { it.name.lowercase().contains(query) }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "人物") },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPerson) {
                Icon(Icons.Rounded.Add, contentDescription = "添加人物")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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

            if (filteredPeople.isEmpty()) {
                EmptyState(
                    title = if (state.people.isEmpty()) "还没有人物" else "没有匹配结果",
                    detail = if (state.people.isEmpty()) {
                        "点击右下角按钮添加第一位人物。"
                    } else {
                        "可以尝试其他姓名、电话或标签。"
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredPeople, key = { it.id }) { person ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPersonClick(person.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                PersonAvatar(person = person, size = 52.dp)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = person.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    val secondary = listOf(person.phone, person.address)
                                        .filter(String::isNotBlank)
                                        .joinToString(" · ")
                                    if (secondary.isNotBlank()) {
                                        Text(
                                            text = secondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val tags = state.tagsByPerson[person.id].orEmpty()
                                    if (tags.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            tags.take(3).forEach { tag ->
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(tag.name) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
