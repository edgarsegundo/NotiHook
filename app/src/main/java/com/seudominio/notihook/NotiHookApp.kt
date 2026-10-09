package com.seudominio.notihook

import android.app.Application
import com.seudominio.notihook.data.AppDatabase
import com.seudominio.notihook.data.Repository
import com.seudominio.notihook.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Dono da única instância do banco e do repositório. */
class NotiHookApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: Repository by lazy { Repository(database.eventDao(), settings) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Limpeza ao abrir. Nao usamos WorkManager de proposito: ele injeta
        // WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED,
        // FOREGROUND_SERVICE e DUMP no manifest, o que contraria a premissa
        // de privilegio minimo deste app por uma limpeza diaria.
        scope.launch {
            try {
                repository.applyRetention()
            } catch (t: Throwable) {
                // Nunca impedir o app de abrir por causa da limpeza.
            }
        }
    }
}
