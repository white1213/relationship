package com.relationship.graph.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.relationship.graph.data.local.BirthdayCalendar
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.Gender
import com.relationship.graph.ui.AppUiState
import com.relationship.graph.ui.RelationshipViewModel
import com.relationship.graph.ui.lunarDayLabel
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
    var gender by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.gender ?: Gender.UNSPECIFIED)
    }
    var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var birthday by rememberSaveable(existing?.id) { mutableStateOf(existing?.birthday.orEmpty()) }
    var address by rememberSaveable(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var tags by rememberSaveable(existing?.id) {
        mutableStateOf(state.tagsByPerson[existing?.id].orEmpty().joinToString("，") { it.name })
    }
    var avatarPath by rememberSaveable(existing?.id) { mutableStateOf(existing?.avatarPath) }
    var photoMenuExpanded by remember { mutableStateOf(false) }

    // 生日/忌日（v6）：农历字段 + 已故标记。
    var birthdayCalendar by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.birthdayCalendar ?: BirthdayCalendar.SOLAR)
    }
    var lunarYearText by rememberSaveable(existing?.id) { mutableStateOf("") }
    var lunarMonth by rememberSaveable(existing?.id) { mutableStateOf(existing?.lunarMonth ?: 1) }
    var lunarDay by rememberSaveable(existing?.id) { mutableStateOf(existing?.lunarDay ?: 1) }
    var lunarLeap by rememberSaveable(existing?.id) { mutableStateOf(existing?.isLeapMonth == true) }
    var isDeceased by rememberSaveable(existing?.id) { mutableStateOf(existing?.deceased == true) }
    var deathDate by rememberSaveable(existing?.id) { mutableStateOf(existing?.deathDate.orEmpty()) }
    var deathCalendar by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.deathCalendar ?: BirthdayCalendar.SOLAR)
    }
    var lunarDeathYearText by rememberSaveable(existing?.id) { mutableStateOf("") }
    var lunarDeathMonth by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.lunarDeathMonth ?: 1)
    }
    var lunarDeathDay by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.lunarDeathDay ?: 1)
    }
    var lunarDeathLeap by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.isLeapDeathMonth == true)
    }

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
            runCatching { viewModel.importAvatarBitmap(id, bitmap) }
                .onSuccess { avatarPath = it }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("性别", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gender.entries.forEach { value ->
                        FilterChip(
                            selected = gender == value,
                            onClick = { gender = value },
                            label = { Text(value.displayName()) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("电话") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "生日",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = birthdayCalendar == BirthdayCalendar.SOLAR,
                    onClick = { birthdayCalendar = BirthdayCalendar.SOLAR },
                    label = { Text("公历") },
                )
                FilterChip(
                    selected = birthdayCalendar == BirthdayCalendar.LUNAR,
                    onClick = { birthdayCalendar = BirthdayCalendar.LUNAR },
                    label = { Text("农历") },
                )
            }
            if (birthdayCalendar == BirthdayCalendar.SOLAR) {
                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it },
                    label = { Text("生日（公历）") },
                    placeholder = { Text("例如 1990-08-16") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = lunarYearText,
                    onValueChange = { lunarYearText = it },
                    label = { Text("出生农历年（可选）") },
                    placeholder = { Text("用于换算公历生日，例如 1990") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IntDropdownField(
                        label = "农历月",
                        value = lunarMonth,
                        count = 12,
                        display = { if (it == 12) "腊月" else if (it == 11) "冬月" else "${it}月" },
                        onSelect = { lunarMonth = it },
                        modifier = Modifier.weight(1f),
                    )
                    IntDropdownField(
                        label = "农历日",
                        value = lunarDay,
                        count = 30,
                        display = ::lunarDayLabel,
                        onSelect = { lunarDay = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = lunarLeap,
                        onClick = { lunarLeap = !lunarLeap },
                        label = { Text("闰${if (lunarMonth == 12) "腊" else lunarMonth}月") },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "闰月生日在无闰月年份按平月提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已故",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = isDeceased, onCheckedChange = { isDeceased = it })
            }
            if (isDeceased) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = deathCalendar == BirthdayCalendar.SOLAR,
                        onClick = { deathCalendar = BirthdayCalendar.SOLAR },
                        label = { Text("忌日（公历）") },
                    )
                    FilterChip(
                        selected = deathCalendar == BirthdayCalendar.LUNAR,
                        onClick = { deathCalendar = BirthdayCalendar.LUNAR },
                        label = { Text("忌日（农历）") },
                    )
                }
                if (deathCalendar == BirthdayCalendar.SOLAR) {
                    OutlinedTextField(
                        value = deathDate,
                        onValueChange = { deathDate = it },
                        label = { Text("忌日（公历）") },
                        placeholder = { Text("例如 2020-01-30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = lunarDeathYearText,
                        onValueChange = { lunarDeathYearText = it },
                        label = { Text("逝世农历年（可选）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IntDropdownField(
                            label = "农历月",
                            value = lunarDeathMonth,
                            count = 12,
                            display = { if (it == 12) "腊月" else if (it == 11) "冬月" else "${it}月" },
                            onSelect = { lunarDeathMonth = it },
                            modifier = Modifier.weight(1f),
                        )
                        IntDropdownField(
                            label = "农历日",
                            value = lunarDeathDay,
                            count = 30,
                            display = ::lunarDayLabel,
                            onSelect = { lunarDeathDay = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    FilterChip(
                        selected = lunarDeathLeap,
                        onClick = { lunarDeathLeap = !lunarDeathLeap },
                        label = { Text("闰${if (lunarDeathMonth == 12) "腊" else lunarDeathMonth}月") },
                    )
                }
            }
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
                        gender = gender,
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
                        birthdayCalendar = birthdayCalendar,
                        lunarMonth = lunarMonth,
                        lunarDay = lunarDay,
                        isLeapMonth = lunarLeap,
                        lunarYear = lunarYearText.trim().toIntOrNull(),
                        isDeceased = isDeceased,
                        deathDate = deathDate,
                        deathCalendar = deathCalendar,
                        lunarDeathMonth = lunarDeathMonth,
                        lunarDeathDay = lunarDeathDay,
                        isLeapDeathMonth = lunarDeathLeap,
                        lunarDeathYear = lunarDeathYearText.trim().toIntOrNull(),
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

private fun Gender.displayName(): String = when (this) {
    Gender.UNSPECIFIED -> "未设置"
    Gender.MALE -> "男"
    Gender.FEMALE -> "女"
}



@Composable
private fun IntDropdownField(
    label: String,
    value: Int,
    count: Int,
    display: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = display(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (1..count).forEach { item ->
                DropdownMenuItem(
                    text = { Text(display(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
