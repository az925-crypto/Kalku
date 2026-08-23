package com.zaaaam.kalku.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zaaaam.kalku.core.PinHasher
import com.zaaaam.kalku.data.SettingsRepo
import kotlinx.coroutines.flow.first

/**
 * Session lock state for the vault. Lives in memory only:
 * a fresh process always starts locked (fail-safe default).
 */
class LockController(private val settings: SettingsRepo) {

    var unlocked by mutableStateOf(false)
        private set

    var pinExists by mutableStateOf<Boolean?>(null) // null = still loading
        private set

    var lastBackgroundedAt by mutableStateOf(0L)
        private set

    suspend fun load() {
        val hash = settings.currentPinHash()
        pinExists = !hash.isNullOrBlank()
    }

    /** Verifies a candidate PIN against the stored hash. */
    suspend fun verifyPin(pin: String): Boolean {
        val hash = settings.currentPinHash()
        return PinHasher.verify(pin, hash)
    }

    suspend fun setPin(pin: String) {
        require(pin.length in 4..16) { "PIN length must be 4-16" }
        settings.setPinHash(PinHasher.hash(pin))
        pinExists = true
        unlocked = true
    }

    fun unlock() { unlocked = true }
    fun lock() { unlocked = false }

    fun onBackground() { lastBackgroundedAt = System.currentTimeMillis() }

    /** Re-lock if auto-lock delay elapsed while backgrounded. 0 disables. */
    suspend fun relockIfExpired(): Boolean {
        val minutes = settings.autoLockMinutes.first()
        if (!unlocked || minutes <= 0) return false
        if (System.currentTimeMillis() - lastBackgroundedAt >= minutes * 60_000L) {
            unlocked = false
            return true
        }
        return false
    }
}
