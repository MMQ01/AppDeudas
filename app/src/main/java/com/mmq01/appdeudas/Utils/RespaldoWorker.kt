package com.mmq01.appdeudas.Utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Respaldo automático de la base. Lo programa [RespaldoScheduler] para las 2:00 a. m. cada
 * 5 días y corre aunque la app esté cerrada. `automatico` evita duplicados el mismo día.
 */
class RespaldoWorker(
    contexto: Context,
    parametros: WorkerParameters
) : Worker(contexto, parametros) {

    override fun doWork(): Result {
        RespaldoUtils.automatico(applicationContext)
        RespaldoScheduler.programarPeriodico(applicationContext)
        return Result.success()
    }
}
