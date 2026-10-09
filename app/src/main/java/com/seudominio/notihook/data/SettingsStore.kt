package com.seudominio.notihook.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notihook_settings")

/** Configurações do app (pacotes monitorados, retenção, pausa). */
class SettingsStore(private val context: Context) {

    private object Keys {
        val MONITORED = stringSetPreferencesKey("monitored_packages")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val PAUSED = booleanPreferencesKey("capture_paused")
    }

    val monitoredPackages: Flow<Set<String>> =
        context.dataStore.data.map { it.readMonitored() }

    val retentionDays: Flow<Int> =
        context.dataStore.data.map { it[Keys.RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS }

    val capturePaused: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PAUSED] ?: false }

    /** Leitura pontual, usada pelo serviço a cada notificação. */
    suspend fun monitoredPackagesNow(): Set<String> =
        context.dataStore.data.first().readMonitored()

    suspend fun isPausedNow(): Boolean =
        context.dataStore.data.first()[Keys.PAUSED] ?: false

    suspend fun retentionDaysNow(): Int =
        context.dataStore.data.first()[Keys.RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS

    suspend fun addPackage(pkg: String) {
        val clean = pkg.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { it[Keys.MONITORED] = it.readMonitored() + clean }
    }

    suspend fun removePackage(pkg: String) {
        context.dataStore.edit { it[Keys.MONITORED] = it.readMonitored() - pkg }
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.RETENTION_DAYS] = days.coerceAtLeast(0) }
    }

    suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { it[Keys.PAUSED] = paused }
    }

    private fun Preferences.readMonitored(): Set<String> =
        this[Keys.MONITORED] ?: setOf(DEFAULT_PACKAGE)

    companion object {
        const val DEFAULT_PACKAGE = "com.whatsapp.w4b"
        const val DEFAULT_RETENTION_DAYS = 30
    }
}
