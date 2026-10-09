package com.seudominio.notihook

import android.app.Application
import com.seudominio.notihook.data.AppDatabase
import com.seudominio.notihook.data.Repository
import com.seudominio.notihook.data.SettingsStore

/** Dono da única instância do banco e do repositório. */
class NotiHookApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: Repository by lazy { Repository(database.eventDao(), settings) }
}
