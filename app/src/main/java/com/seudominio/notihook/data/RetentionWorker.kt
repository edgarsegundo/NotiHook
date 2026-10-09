package com.seudominio.notihook.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seudominio.notihook.NotiHookApp
import java.util.concurrent.TimeUnit

/** Limpeza periódica conforme a retenção configurada. */
class RetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        try {
            (applicationContext as NotiHookApp).repository.applyRetention()
            Result.success()
        } catch (t: Throwable) {
            Result.failure()
        }

    companion object {
        private const val NAME = "notihook-retention"

        /**
         * Agenda a limpeza diária. Falhar aqui não pode impedir o app de abrir
         * (o WorkManager pode não estar inicializado, p.ex. em testes), e a
         * limpeza ao abrir já cobre o caso.
         */
        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<RetentionWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().build())
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            } catch (t: Throwable) {
                Log.w("NotiHook", "nao foi possivel agendar a limpeza periodica", t)
            }
        }
    }
}
