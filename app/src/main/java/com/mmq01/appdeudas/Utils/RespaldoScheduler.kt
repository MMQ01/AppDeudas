package com.mmq01.appdeudas.Utils

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Programa el respaldo automático cada 5 días a las 2:00 a. m. (hora del celular).
 *
 * Usa `WorkManager` (JobScheduler): corre con la app cerrada, sobrevive reinicios y no pide
 * permisos. No es exacto: Android puede ejecutarlo unos minutos después, y más si el equipo
 * está en Doze o con la batería restringida.
 *
 * Dos etapas: un trabajo único para la próxima 2:00 (así el primer respaldo no se salta el
 * intervalo) y, al terminar, el trabajo periódico de 5 días. `KEEP` evita duplicar o reiniciar.
 */
object RespaldoScheduler {

    private const val INICIAL = "respaldo_inicial"
    private const val PERIODICO = "respaldo_periodico"
    private const val ANTERIOR = "respaldo_diario"

    private const val HORA = 2
    private const val CADA_DIAS = 5L
    private const val MARGEN_MINUTOS = 5L

    fun asegurar(context: Context) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)

        workManager.cancelUniqueWork(ANTERIOR)

        val futuro = workManager.getWorkInfosForUniqueWork(PERIODICO)
        futuro.addListener({
            val activo = try {
                futuro.get().any { info -> !info.state.isFinished }
            } catch (e: Exception) {
                false
            }
            if (!activo) {
                workManager.enqueueUniqueWork(
                    INICIAL,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<RespaldoWorker>()
                        .setInitialDelay(millisHastaLas2(), TimeUnit.MILLISECONDS)
                        .build()
                )
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Se llama desde el worker: deja lista la repetición cada 5 días a la misma hora. */
    fun programarPeriodico(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODICO,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RespaldoWorker>(
                CADA_DIAS, TimeUnit.DAYS,
                MARGEN_MINUTOS, TimeUnit.MINUTES
            ).build()
        )
    }

    /** Tiempo hasta la próxima 2:00 a. m. según la hora del equipo. */
    fun millisHastaLas2(ahora: Calendar = Calendar.getInstance()): Long {
        val proxima = (ahora.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, HORA)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(ahora)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return proxima.timeInMillis - ahora.timeInMillis
    }
}
