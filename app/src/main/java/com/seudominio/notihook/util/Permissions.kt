package com.seudominio.notihook.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object Permissions {

    /** O acesso a notificações está concedido a este app? */
    fun hasNotificationAccess(context: Context): Boolean =
        try {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        } catch (t: Throwable) {
            false
        }

    fun openNotificationAccessSettings(context: Context) {
        open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * Abre a lista de otimização de bateria. Usa ACTION_IGNORE_BATTERY_-
     * OPTIMIZATION_SETTINGS de propósito: a variante REQUEST_... exige
     * permissão e é motivo de rejeição na Play Store.
     */
    fun openBatterySettings(context: Context) {
        open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            // Alguns OEMs não expõem a tela; sem crash.
        }
    }
}
