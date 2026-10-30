package com.mmq01.appdeudas

import com.mmq01.appdeudas.Clases.DeudaPendiente
import com.mmq01.appdeudas.Clases.FuenteFavor
import com.mmq01.appdeudas.Utils.RepartoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepartoUtilsTest {

    private val mercado = DeudaPendiente(1, "Mercado", "2026-09-12 10:00:00", 150_000)
    private val prestamo = DeudaPendiente(2, "Préstamo", "2026-09-20 10:00:00", 300_000)

    @Test
    fun reparte_primero_a_la_mas_antigua() {
        val r = RepartoUtils.fifo(listOf(prestamo, mercado), 200_000)

        assertEquals(listOf(1L, 2L), r.lineas.map { it.idDeuda })
        assertEquals(listOf(150_000L, 50_000L), r.lineas.map { it.monto })
        assertEquals(0, r.saldoFavor)
        assertTrue(r.lineas[0].quedaPagada)
        assertFalse(r.lineas[1].quedaPagada)
    }

    @Test
    fun pago_que_no_alcanza_la_primera_deuda_no_toca_las_otras() {
        val r = RepartoUtils.fifo(listOf(mercado, prestamo), 100_000)

        assertEquals(listOf(100_000L, 0L), r.lineas.map { it.monto })
        assertEquals(0, r.saldoFavor)
    }

    @Test
    fun valor_mayor_al_pendiente_queda_a_favor() {
        val r = RepartoUtils.fifo(listOf(mercado, prestamo), 500_000)

        assertEquals(450_000, r.totalAsignado)
        assertEquals(50_000, r.saldoFavor)
    }

    @Test
    fun desempata_por_id_cuando_la_fecha_es_igual() {
        val a = DeudaPendiente(7, "A", "2026-09-12 10:00:00", 10_000)
        val b = DeudaPendiente(3, "B", "2026-09-12 10:00:00", 10_000)

        val r = RepartoUtils.fifo(listOf(a, b), 10_000)

        assertEquals(listOf(3L, 7L), r.lineas.map { it.idDeuda })
        assertEquals(listOf(10_000L, 0L), r.lineas.map { it.monto })
    }

    @Test
    fun ignora_deudas_sin_saldo_y_valores_no_positivos() {
        val saldada = DeudaPendiente(9, "Saldada", "2026-01-01 00:00:00", 0)

        assertEquals(listOf(1L), RepartoUtils.fifo(listOf(saldada, mercado), 1).lineas.map { it.idDeuda })
        assertEquals(0, RepartoUtils.fifo(listOf(mercado), -5).totalAsignado)
    }

    @Test
    fun validar_acepta_reparto_exacto_o_con_saldo_a_favor() {
        val base = RepartoUtils.fifo(listOf(mercado, prestamo), 200_000).lineas

        assertNull(RepartoUtils.validar(base, 200_000))
        assertNotNull(RepartoUtils.validar(base, 0))
        assertNull(RepartoUtils.validar(base, 250_000))
        assertNotNull(RepartoUtils.validar(base, 150_000))
        assertNotNull(RepartoUtils.validar(base.map { it.copy(monto = it.saldo + 1) }, 150_001))
        assertNotNull(RepartoUtils.validar(base.map { it.copy(monto = -1) }, 0))
        assertNull(RepartoUtils.validar(base.map { it.copy(monto = 0) }, 50_000))
    }

    @Test
    fun nota_del_abono_general_dice_de_cuanto_fue_el_pago() {
        assertEquals(
            "Abono general de $ 20.000 · aplicado aquí $ 300 · repartido en 3 deudas · a favor $ 4.700 · Nequi",
            RepartoUtils.notaAbonoGeneral(20_000, 300, 3, 4_700, "  Nequi ")
        )
        assertEquals(
            "Abono general de $ 1.250.000 · aplicado aquí $ 1.250.000",
            RepartoUtils.notaAbonoGeneral(1_250_000, 1_250_000, 1, 0, " ")
        )
    }

    @Test
    fun nota_de_saldo_a_favor_incluye_valor_y_fecha_del_pago() {
        assertEquals(
            "Saldo a favor del abono general de $ 20.000 (28/10/2026)",
            RepartoUtils.notaSaldoFavor(20_000, "2026-10-28 15:04:00")
        )
    }

    @Test
    fun repartir_favor_toma_del_pago_mas_antiguo_y_no_pasa_de_lo_disponible() {
        val viejo = FuenteFavor(5, 20_000, "2026-10-01 10:00:00", 3_000)
        val nuevo = FuenteFavor(9, 10_000, "2026-10-20 10:00:00", 4_000)
        val lineas = RepartoUtils.fifo(listOf(mercado, prestamo), 450_000).lineas
            .map { it.copy(monto = 5_000) }

        val r = RepartoUtils.repartirFavor(listOf(nuevo, viejo), lineas)

        assertEquals(listOf(5L to 1L, 9L to 1L, 9L to 2L), r.map { it.fuente.idPago to it.idDeuda })
        assertEquals(listOf(3_000L, 2_000L, 2_000L), r.map { it.monto })
    }
}
