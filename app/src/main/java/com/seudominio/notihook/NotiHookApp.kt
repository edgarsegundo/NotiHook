package com.seudominio.notihook

import android.app.Application
import com.seudominio.notihook.data.AppDatabase
import com.seudominio.notihook.data.Repository
import com.seudominio.notihook.data.RetentionWorker
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
        // Limpeza ao abrir, e depois a cada 24h via WorkManager.
        scope.launch {
            try {
                repository.applyRetention()
            } catch (t: Throwable) {
                // Nunca impedir o app de abrir por causa da limpeza.
            }
        }
        RetentionWorker.schedule(this)
    }
}
