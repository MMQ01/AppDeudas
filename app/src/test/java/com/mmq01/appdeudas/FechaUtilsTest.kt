package com.mmq01.appdeudas

import com.mmq01.appdeudas.Utils.FechaUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class FechaUtilsTest {

    private lateinit var zonaOriginal: TimeZone

    @Before
    fun fijarZona() {
        zonaOriginal = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))
    }

    @After
    fun restaurarZona() {
        TimeZone.setDefault(zonaOriginal)
    }

    @Test
    fun convierte_utc_al_dia_local() {
        assertEquals("29/10/2026", FechaUtils.diaLocal("2026-10-30 03:16:44"))
        assertEquals("12/09/2026", FechaUtils.diaLocal("2026-09-12 08:00:00"))
    }

    @Test
    fun madrugada_utc_pertenece_al_dia_anterior_local() {
        assertEquals("11/09/2026", FechaUtils.diaLocal("2026-09-12 02:00:00"))
    }

    @Test
    fun acepta_fecha_sin_hora() {
        assertEquals("12/09/2026", FechaUtils.diaLocal("2026-09-12"))
        assertEquals("12/09/2026", FechaUtils.corta("2026-09-12"))
    }

    @Test
    fun texto_invalido_o_vacio_no_revienta() {
        assertEquals("", FechaUtils.diaLocal(null))
        assertEquals("", FechaUtils.diaLocal(""))
        assertEquals("sin-fecha", FechaUtils.diaLocal("sin-fecha"))
    }
}
