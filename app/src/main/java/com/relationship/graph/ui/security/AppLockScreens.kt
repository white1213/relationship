package com.relationship.graph.ui.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.relationship.graph.security.AppLockState
import kotlinx.coroutines.delay

@Composable
fun PinSetupScreen(
    biometricAvailable: Boolean,
    onSetup: (pin: String, biometricEnabled: Boolean) -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var biometricEnabled by rememberSaveable { mutableStateOf(biometricAvailable) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text("关系图谱", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 12 && it.all(Char::isDigit)) {
                        pin = it
                        error = null
                    }
                },
                label = { Text("设置 6 至 12 位数字密码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmation,
                onValueChange = {
                    if (it.length <= 12 && it.all(Char::isDigit)) {
                        confirmation = it
                        error = null
                    }
                },
                label = { Text("再次输入密码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (biometricAvailable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                    Text("启用指纹或面容解锁")
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    error = when {
                        pin.length < 6 -> "密码至少需要 6 位数字"
                        pin != confirmation -> "两次输入的密码不一致"
                        else -> null
                    }
                    if (error == null) onSetup(pin, biometricEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始使用")
            }
        }
    }
}

@Composable
fun PinUnlockScreen(
    state: AppLockState,
    onUnlock: (String) -> Unit,
    onBiometricUnlock: () -> Unit,
    onClearError: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var remainingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.cooldownUntil) {
        while (state.cooldownUntil > System.currentTimeMillis()) {
            remainingSeconds = (state.cooldownUntil - System.currentTimeMillis() + 999) / 1_000
            delay(250)
        }
        remainingSeconds = 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("关系图谱", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 12 && it.all(Char::isDigit)) {
                        pin = it
                        onClearError()
                    }
                },
                label = { Text("输入密码") },
                singleLine = true,
                enabled = remainingSeconds == 0L,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (remainingSeconds > 0L) {
                Text(
                    text = "请等待 $remainingSeconds 秒",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            } else {
                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onUnlock(pin) },
                enabled = pin.length >= 6 && remainingSeconds == 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("解锁")
            }
            if (state.biometricEnabled && state.biometricAvailable) {
                Spacer(Modifier.height(12.dp))
                val launchBiometric = rememberBiometricPromptLauncher(
                    title = "解锁关系图谱",
                    onSuccess = onBiometricUnlock,
                )
                OutlinedButton(
                    onClick = launchBiometric,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("使用指纹或面容")
                }
            }
        }
    }
}

@Composable
fun rememberBiometricPromptLauncher(
    title: String,
    onSuccess: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return {}
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    currentOnSuccess()
                }
            },
        )
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .setNegativeButtonText("取消")
        .build()
    return { prompt.authenticate(promptInfo) }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
