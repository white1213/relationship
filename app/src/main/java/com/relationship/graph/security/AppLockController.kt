package com.relationship.graph.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import com.relationship.graph.data.security.PinVerification
import com.relationship.graph.data.security.SecurityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LockPhase {
    LOADING,
    SETUP,
    LOCKED,
    UNLOCKED,
}

data class AppLockState(
    val phase: LockPhase = LockPhase.LOADING,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val cooldownUntil: Long = 0L,
    val errorMessage: String? = null,
)

class AppLockController(
    private val context: Context,
    private val securityStore: SecurityStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    private var lastBackgroundAt = 0L
    private var initialized = false

    init {
        val biometricAvailable =
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS
        _state.update { it.copy(biometricAvailable = biometricAvailable) }
        scope.launch {
            securityStore.snapshot.collect { snapshot ->
                _state.update {
                    it.copy(
                        biometricEnabled = snapshot.biometricEnabled,
                        cooldownUntil = snapshot.cooldownUntil,
                    )
                }
                if (!initialized) {
                    initialized = true
                    _state.update {
                        it.copy(phase = if (snapshot.hasPin) LockPhase.LOCKED else LockPhase.SETUP)
                    }
                }
            }
        }
    }

    fun setupPin(pin: String, biometricEnabled: Boolean) {
        scope.launch {
            securityStore.createPin(pin)
            securityStore.setBiometricEnabled(biometricEnabled)
            _state.update {
                it.copy(
                    phase = LockPhase.UNLOCKED,
                    biometricEnabled = biometricEnabled,
                    cooldownUntil = 0L,
                    errorMessage = null,
                )
            }
        }
    }

    fun unlock(pin: String) {
        scope.launch {
            when (val result = securityStore.verifyPin(pin)) {
                PinVerification.Success -> {
                    _state.update {
                        it.copy(
                            phase = LockPhase.UNLOCKED,
                            cooldownUntil = 0L,
                            errorMessage = null,
                        )
                    }
                }
                is PinVerification.CoolingDown -> {
                    _state.update {
                        it.copy(
                            cooldownUntil = result.until,
                            errorMessage = "尝试次数过多，请稍后再试",
                        )
                    }
                }
                is PinVerification.Invalid -> {
                    _state.update {
                        it.copy(
                            errorMessage = if (result.remainingAttempts > 0) {
                                "密码错误，还可尝试 ${result.remainingAttempts} 次"
                            } else {
                                "密码错误"
                            },
                        )
                    }
                }
            }
        }
    }

    fun unlockWithBiometric() {
        if (_state.value.biometricEnabled && _state.value.biometricAvailable) {
            _state.update { it.copy(phase = LockPhase.UNLOCKED, errorMessage = null) }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        scope.launch {
            securityStore.setBiometricEnabled(enabled)
            _state.update { it.copy(biometricEnabled = enabled) }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun onBackground() {
        lastBackgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        if (_state.value.phase != LockPhase.UNLOCKED) return
        if (lastBackgroundAt > 0L &&
            SystemClock.elapsedRealtime() - lastBackgroundAt >= LOCK_TIMEOUT_MS
        ) {
            lockNow()
        }
    }

    fun lockNow() {
        if (_state.value.phase == LockPhase.UNLOCKED) {
            _state.update { it.copy(phase = LockPhase.LOCKED, errorMessage = null) }
        }
    }

    private companion object {
        const val LOCK_TIMEOUT_MS = 60_000L
        const val BIOMETRIC_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}
