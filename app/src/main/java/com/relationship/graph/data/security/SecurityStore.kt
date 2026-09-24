package com.relationship.graph.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore by preferencesDataStore(name = "security")

data class SecuritySnapshot(
    val hasPin: Boolean = false,
    val biometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    val cooldownUntil: Long = 0L,
)

sealed interface PinVerification {
    data object Success : PinVerification
    data class Invalid(val remainingAttempts: Int) : PinVerification
    data class CoolingDown(val until: Long) : PinVerification
}

class SecurityStore(context: Context) {
    private val dataStore = context.securityDataStore

    val snapshot: Flow<SecuritySnapshot> = dataStore.data.map { preferences ->
        SecuritySnapshot(
            hasPin = preferences[PIN_HASH] != null,
            biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: true,
            failedAttempts = preferences[FAILED_ATTEMPTS] ?: 0,
            cooldownUntil = preferences[COOLDOWN_UNTIL] ?: 0L,
        )
    }

    suspend fun createPin(pin: String) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        dataStore.edit { preferences ->
            preferences[PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            preferences[PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
            preferences[FAILED_ATTEMPTS] = 0
            preferences[COOLDOWN_UNTIL] = 0L
        }
    }

    suspend fun verifyPin(pin: String): PinVerification {
        val preferences = dataStore.data.first()
        val now = System.currentTimeMillis()
        val cooldownUntil = preferences[COOLDOWN_UNTIL] ?: 0L
        if (cooldownUntil > now) {
            return PinVerification.CoolingDown(cooldownUntil)
        }

        val salt = preferences[PIN_SALT]?.let { Base64.decode(it, Base64.NO_WRAP) }
        val expected = preferences[PIN_HASH]?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (salt == null || expected == null) return PinVerification.Invalid(MAX_ATTEMPTS)

        val actual = derive(pin, salt)
        if (actual.contentEquals(expected)) {
            dataStore.edit {
                it[FAILED_ATTEMPTS] = 0
                it[COOLDOWN_UNTIL] = 0L
            }
            return PinVerification.Success
        }

        val failedAttempts = (preferences[FAILED_ATTEMPTS] ?: 0) + 1
        val nextCooldown = if (failedAttempts >= MAX_ATTEMPTS) {
            val cooldownSeconds = minOf(300L, 30L * (1L shl minOf(4, failedAttempts - MAX_ATTEMPTS)))
            now + cooldownSeconds * 1_000L
        } else {
            0L
        }
        dataStore.edit {
            it[FAILED_ATTEMPTS] = if (failedAttempts >= MAX_ATTEMPTS) 0 else failedAttempts
            it[COOLDOWN_UNTIL] = nextCooldown
        }
        return if (nextCooldown > now) {
            PinVerification.CoolingDown(nextCooldown)
        } else {
            PinVerification.Invalid(MAX_ATTEMPTS - failedAttempts)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private companion object {
        const val PBKDF2_ITERATIONS = 120_000
        const val MAX_ATTEMPTS = 5
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val COOLDOWN_UNTIL = longPreferencesKey("cooldown_until")
    }
}
