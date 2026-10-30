package com.mmq01.appdeudas

import com.mmq01.appdeudas.Utils.RespaldoScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RespaldoSchedulerTest {

    private val unaHora = 60L * 60 * 1000
    private val unDia = 24 * unaHora

    private fun momento(hora: Int, minuto: Int, segundo: Int = 0): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("America/Bogota")).apply {
            set(2026, Calendar.OCTOBER, 29, hora, minuto, segundo)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun antes_de_las_dos_espera_hasta_hoy() {
        assertEquals(unaHora, RespaldoScheduler.millisHastaLas2(momento(1, 0)))
    }

    @Test
    fun exactamente_las_dos_espera_un_dia() {
        assertEquals(unDia, RespaldoScheduler.millisHastaLas2(momento(2, 0)))
    }

    @Test
    fun despues_de_las_dos_espera_hasta_manana() {
        assertEquals(23 * unaHora, RespaldoScheduler.millisHastaLas2(momento(3, 0)))
    }

    @Test
    fun un_minuto_antes_espera_un_minuto() {
        assertEquals(60_000L, RespaldoScheduler.millisHastaLas2(momento(1, 59)))
    }

    @Test
    fun medio_minuto_antes_espera_medio_minuto() {
        assertEquals(30_000L, RespaldoScheduler.millisHastaLas2(momento(1, 59, 30)))
    }
}
