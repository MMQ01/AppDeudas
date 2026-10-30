package com.mmq01.appdeudas

import com.mmq01.appdeudas.Clases.DeudaDetalle
import com.mmq01.appdeudas.Utils.ResumenUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class ResumenUtilsTest {

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
    fun arma_lista_con_descripcion_y_total() {
        val deudas = listOf(
            deuda(
                id = 2,
                nombre = "Préstamo",
                saldo = 300_000.0,
                descripcion = "Cuota de la moto",
                fecha = "2026-09-20 08:00:00"
            ),
            deuda(id = 1, nombre = "Mercado", saldo = 150_000.0)
        )

        val esperado = "• Mercado (12/09/2026) — $ 150.000\n" +
            "• Préstamo (20/09/2026) — $ 300.000\n" +
            "  Cuota de la moto\n" +
            "\n" +
            "*Total pendiente:* $ 450.000"

        assertEquals(esperado, ResumenUtils.construir(deudas))
    }

    @Test
    fun ordena_de_la_mas_antigua_a_la_mas_reciente() {
        val deudas = listOf(
            deuda(id = 3, nombre = "Reciente", fecha = "2026-10-01 09:00:00", saldo = 10_000.0),
            deuda(id = 1, nombre = "Vieja", fecha = "2026-08-01 09:00:00", saldo = 20_000.0),
            deuda(id = 2, nombre = "Media", fecha = "2026-09-01 09:00:00", saldo = 30_000.0)
        )

        val texto = ResumenUtils.construir(deudas)
        val posVieja = texto.indexOf("Vieja")
        val posMedia = texto.indexOf("Media")
        val posReciente = texto.indexOf("Reciente")

        assertEquals(true, posVieja < posMedia && posMedia < posReciente)
    }

    @Test
    fun omite_descripcion_vacia() {
        val texto = ResumenUtils.construir(
            listOf(deuda(nombre = "Mercado", descripcion = "   "))
        )

        assertEquals("• Mercado (12/09/2026) — $ 150.000\n\n*Total pendiente:* $ 150.000", texto)
    }

    @Test
    fun sin_deudas_solo_muestra_total_en_cero() {
        assertEquals("*Total pendiente:* $ 0", ResumenUtils.construir(emptyList()))
    }

    @Test
    fun ignora_deudas_sin_saldo() {
        val texto = ResumenUtils.construir(
            listOf(
                deuda(nombre = "Pagada", saldo = 0.0),
                deuda(nombre = "Mercado", saldo = 150_000.0)
            )
        )

        assertEquals("• Mercado (12/09/2026) — $ 150.000\n\n*Total pendiente:* $ 150.000", texto)
    }

    @Test
    fun usa_la_fecha_local_del_equipo() {
        val texto = ResumenUtils.construir(
            listOf(deuda(nombre = "Mercado", fecha = "2026-10-30 03:16:44"))
        )
        assertTrue(texto.contains("(29/10/2026)"))
    }

    @Test
    fun convierte_fecha_a_dd_mm_aaaa() {
        assertEquals("12/09/2026", ResumenUtils.fechaCorta("2026-09-12 08:00:00"))
        assertEquals("12/09/2026", ResumenUtils.fechaCorta("2026-09-12"))
        assertEquals("sin-fecha", ResumenUtils.fechaCorta("sin-fecha"))
    }

    private fun deuda(
        id: Long = 1,
        nombre: String = "Mercado",
        descripcion: String? = null,
        saldo: Double = 150_000.0,
        fecha: String = "2026-09-12 08:00:00"
    ) = DeudaDetalle(
        id = id,
        idCliente = "573001234567",
        nombre = nombre,
        descripcion = descripcion,
        valor = saldo,
        saldo = saldo,
        estado = "pendiente",
        fechaCreacion = fecha,
        fechaPago = null,
        fechaAnulacion = null,
        fechaModificacion = fecha,
        totalAbonado = 0.0,
        numAbonos = 0
    )
}
