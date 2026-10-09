package com.seudominio.notihook.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.seudominio.notihook.BuildConfig
import com.seudominio.notihook.NotiHookApp
import com.seudominio.notihook.data.EventType
import com.seudominio.notihook.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Captura as notificações dos pacotes monitorados.
 *
 * Regras: o callback nunca bloqueia (todo o trabalho de banco vai para um
 * escopo de IO próprio) e o serviço nunca crasha (todo o parsing é envolvido
 * em try/catch; falhas viram um evento com `parse_error`).
 */
class WaListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: Repository
        get() = (application as NotiHookApp).repository

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        repository.setListenerConnected(true)
        if (BuildConfig.DEBUG) Log.d(TAG, "listener conectado")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        repository.setListenerConnected(false)
        if (BuildConfig.DEBUG) Log.d(TAG, "listener desconectado; pedindo rebind")
        try {
            requestRebind(ComponentName(this, WaListenerService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "requestRebind falhou", t)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handle(sbn, EventType.POSTED, null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        handle(sbn, EventType.REMOVED, reason)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Coberto pela sobrecarga com `reason`; aqui só para completude em APIs antigas.
        handle(sbn, EventType.REMOVED, null)
    }

    /** Importa as notificações ativas como eventos SNAPSHOT. */
    fun importActive(): Int {
        val active = try {
            activeNotifications
        } catch (t: Throwable) {
            Log.w(TAG, "getActiveNotifications falhou", t)
            return 0
        } ?: return 0

        var count = 0
        for (sbn in active) {
            handle(sbn, EventType.SNAPSHOT, null)
            count++
        }
        return count
    }

    private fun handle(sbn: StatusBarNotification?, eventType: String, reason: Int?) {
        if (sbn == null) return
        // Lido fora da coroutine: o objeto sbn pode não sobreviver ao callback.
        val pkg = try {
            sbn.packageName
        } catch (t: Throwable) {
            return
        }

        scope.launch {
            try {
                if (repository.settings.isPausedNow()) return@launch
                if (pkg !in repository.settings.monitoredPackagesNow()) return@launch

                val result = NotificationParser.parse(sbn, eventType, reason)
                repository.saveEvent(result.event, result.messages)

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "$eventType $pkg title=${result.event.title} text=${result.event.text}")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "falha ao processar notificação", t)
                // Nunca perder o registro: grava o evento de erro.
                try {
                    repository.saveEvent(
                        NotificationParser.errorEvent(sbn, eventType, t),
                        emptyList(),
                    )
                } catch (inner: Throwable) {
                    Log.e(TAG, "falha ao gravar evento de erro", inner)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (instance === this) instance = null
        repository.setListenerConnected(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotiHook"

        /**
         * Instância viva do serviço, para a UI poder pedir o snapshot das
         * ativas. É null quando o sistema não mantém o listener ligado.
         */
        @Volatile
        var instance: WaListenerService? = null
            private set
    }
}
