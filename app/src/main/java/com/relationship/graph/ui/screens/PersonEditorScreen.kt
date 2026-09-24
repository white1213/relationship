package com.relationship.graph.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.components.AppTopBar
import com.relationship.graph.ui.components.PersonAvatar
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PersonEditorScreen(
    state: AppUiState,
    personId: String?,
    viewModel: RelationshipViewModel,
    onBack: () -> Unit,
) {
    val existing: PersonEntity? = state.person(personId)
    val id = existing?.id ?: remember(personId) { UUID.randomUUID().toString() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var birthday by rememberSaveable(existing?.id) { mutableStateOf(existing?.birthday.orEmpty()) }
    var address by rememberSaveable(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var tags by rememberSaveable(existing?.id) {
        mutableStateOf(state.tagsByPerson[existing?.id].orEmpty().joinToString("，") { it.name })
    }
    var avatarPath by rememberSaveable(existing?.id) { mutableStateOf(existing?.avatarPath) }
    var photoMenuExpanded by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { viewModel.importAvatarFromUri(id, uri) }
                .onSuccess { avatarPath = it }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        scope.launch {
            avatarPath = viewModel.importAvatarBitmap(id, bitmap)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (existing == null) "添加人物" else "编辑人物",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Box {
                    PersonAvatar(
                        person = PersonEntity(
                            id = id,
                            name = name.ifBlank { "?" },
                            avatarPath = avatarPath,
                        ),
                        size = 100.dp,
                        modifier = Modifier.clickable { photoMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = photoMenuExpanded,
                        onDismissRequest = { photoMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("从相册选择") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Collections, contentDescription = null)
                            },
                            onClick = {
                                photoMenuExpanded = false
                                galleryLauncher.launch("image/*")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("拍照") },
                            leadingIcon = {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            },
                            onClick = {
                                photoMenuExpanded = false
                                val permissionGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (permissionGranted) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        )
                    }
                }
            }

            Button(
                onClick = { photoMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(if (avatarPath == null) "添加头像" else "更换头像")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("姓名（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("电话") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = birthday,
                onValueChange = { birthday = it },
                label = { Text("生日") },
                placeholder = { Text("例如 1990-08-16") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("地址") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("标签") },
                supportingText = { Text("多个标签用逗号或顿号分隔") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.savePerson(
                        id = id,
                        name = name,
                        avatarPath = avatarPath,
                        phone = phone,
                        birthday = birthday,
                        address = address,
                        notes = notes,
                        tagNames = tags
                            .split(',', '，', '、')
                            .map(String::trim)
                            .filter(String::isNotEmpty),
                        existing = existing,
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
