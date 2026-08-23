package com.zaaaam.kalku.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kalku_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** All user preferences. Persisted app-locally (not part of the portable vault data). */
class SettingsRepo(private val context: Context) {

    private object K {
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val angleDefault = stringPreferencesKey("angle_default")
        val haptics = booleanPreferencesKey("haptics")
        val precision = intPreferencesKey("precision")
        val historyLimit = intPreferencesKey("history_limit")
        val pinHash = stringPreferencesKey("pin_hash")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
        val autoLockMinutes = intPreferencesKey("auto_lock_minutes")
        val editorFontSize = intPreferencesKey("editor_font_size")
        val editorWordWrap = booleanPreferencesKey("editor_word_wrap")
        val editorLineNumbers = booleanPreferencesKey("editor_line_numbers")
        val editorTabSize = intPreferencesKey("editor_tab_size")
        val trashRetentionDays = intPreferencesKey("trash_retention_days")
        val themePack = stringPreferencesKey("theme_pack")
        val vaultIntroShown = booleanPreferencesKey("vault_intro_shown")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[K.themeMode] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    val accent: Flow<String> = context.dataStore.data.map { it[K.accent] ?: "teal" }
    val angleDefault: Flow<String> = context.dataStore.data.map { it[K.angleDefault] ?: "DEG" }
    val haptics: Flow<Boolean> = context.dataStore.data.map { it[K.haptics] ?: true }
    val precision: Flow<Int> = context.dataStore.data.map { it[K.precision] ?: 10 }
    val historyLimit: Flow<Int> = context.dataStore.data.map { it[K.historyLimit] ?: 100 }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[K.pinHash] }
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.biometricEnabled] ?: false }
    val autoLockMinutes: Flow<Int> = context.dataStore.data.map { it[K.autoLockMinutes] ?: 5 }
    val editorFontSize: Flow<Int> = context.dataStore.data.map { it[K.editorFontSize] ?: 14 }
    val editorWordWrap: Flow<Boolean> = context.dataStore.data.map { it[K.editorWordWrap] ?: true }
    val editorLineNumbers: Flow<Boolean> = context.dataStore.data.map { it[K.editorLineNumbers] ?: true }
    val editorTabSize: Flow<Int> = context.dataStore.data.map { it[K.editorTabSize] ?: 4 }
    val trashRetentionDays: Flow<Int> = context.dataStore.data.map { it[K.trashRetentionDays] ?: 30 }
    val themePack: Flow<String> = context.dataStore.data.map { it[K.themePack] ?: "PRECISION" }
    val vaultIntroShown: Flow<Boolean> = context.dataStore.data.map { it[K.vaultIntroShown] ?: false }

    suspend fun currentPinHash(): String? = pinHash.first()

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[K.themeMode] = v.name }
    suspend fun setAccent(v: String) = context.dataStore.edit { it[K.accent] = v }
    suspend fun setAngleDefault(v: String) = context.dataStore.edit { it[K.angleDefault] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setPrecision(v: Int) = context.dataStore.edit { it[K.precision] = v.coerceIn(0, 12) }
    suspend fun setHistoryLimit(v: Int) = context.dataStore.edit { it[K.historyLimit] = v.coerceIn(0, 500) }
    suspend fun setPinHash(hash: String?) = context.dataStore.edit { p ->
        if (hash == null) p.remove(K.pinHash) else p[K.pinHash] = hash
    }
    suspend fun setBiometricEnabled(v: Boolean) = context.dataStore.edit { it[K.biometricEnabled] = v }
    suspend fun setAutoLockMinutes(v: Int) = context.dataStore.edit { it[K.autoLockMinutes] = v.coerceIn(0, 240) }
    suspend fun setEditorFontSize(v: Int) = context.dataStore.edit { it[K.editorFontSize] = v.coerceIn(10, 32) }
    suspend fun setEditorWordWrap(v: Boolean) = context.dataStore.edit { it[K.editorWordWrap] = v }
    suspend fun setEditorLineNumbers(v: Boolean) = context.dataStore.edit { it[K.editorLineNumbers] = v }
    suspend fun setEditorTabSize(v: Int) = context.dataStore.edit { it[K.editorTabSize] = v.coerceIn(2, 8) }
    suspend fun setTrashRetentionDays(v: Int) = context.dataStore.edit { it[K.trashRetentionDays] = v.coerceIn(0, 365) }
    suspend fun setThemePack(v: String) = context.dataStore.edit { it[K.themePack] = v }
    suspend fun setVaultIntroShown() = context.dataStore.edit { it[K.vaultIntroShown] = true }
}
