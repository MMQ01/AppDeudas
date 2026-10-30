package com.mmq01.appdeudas

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmq01.appdeudas.Clases.Cliente
import com.mmq01.appdeudas.DBHelper.AbonoDao
import com.mmq01.appdeudas.DBHelper.ClienteDao
import com.mmq01.appdeudas.DBHelper.DeudaDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.DBHelper.PagoDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PagoDaoTest {

    private lateinit var contexto: Context
    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var deudaDao: DeudaDao
    private lateinit var abonoDao: AbonoDao
    private lateinit var pagoDao: PagoDao

    private val idCliente = "573001234567"
    private var mercado = 0L
    private var prestamo = 0L

    @Before
    fun setUp() {
        contexto = InstrumentationRegistry.getInstrumentation().targetContext
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
        helper = DeudasDatabaseHelper(contexto)
        ClienteDao(helper).insertar(Cliente(idCliente = idCliente, nombre = "Ana"))
        deudaDao = DeudaDao(helper)
        abonoDao = AbonoDao(helper)
        pagoDao = PagoDao(helper)
        mercado = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        prestamo = deudaDao.insertar(idCliente, "Préstamo", null, 300_000.0)
    }

    @After
    fun tearDown() {
        helper.close()
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
    }

    @Test
    fun previsualizar_usa_los_saldos_pendientes() {
        val reparto = pagoDao.previsualizar(idCliente, 200_000)

        assertEquals(450_000, pagoDao.totalPendiente(idCliente))
        assertEquals(listOf(mercado, prestamo), reparto.lineas.map { it.idDeuda })
        assertEquals(listOf(150_000L, 50_000L), reparto.lineas.map { it.monto })
    }

    @Test
    fun registrar_crea_pago_y_abonos_y_recalcula_saldos() {
        val reparto = pagoDao.previsualizar(idCliente, 200_000)
        val idPago = pagoDao.registrar(idCliente, 200_000, reparto.lineas, "Consignación")

        val pago = pagoDao.obtener(idPago)!!
        assertEquals(200_000.0, pago.valor, 0.01)
        assertEquals(200_000.0, pago.aplicado, 0.01)
        assertEquals(0.0, pago.saldoFavor, 0.01)
        assertEquals(2, pago.numDeudas)
        assertEquals("Consignación", pago.nota)
        assertEquals("pagada", deudaDao.obtener(mercado)!!.estado)
        assertEquals(250_000.0, deudaDao.obtener(prestamo)!!.saldo, 0.01)
        assertEquals(idPago, abonoDao.listar(prestamo).single().idPago)
        assertEquals(listOf(idPago), pagoDao.listar(idCliente).map { it.id })
    }

    @Test
    fun registrar_omite_lineas_en_cero() {
        val reparto = pagoDao.previsualizar(idCliente, 100_000)
        val idPago = pagoDao.registrar(idCliente, 100_000, reparto.lineas, null)

        assertEquals(1, pagoDao.obtener(idPago)!!.numDeudas)
        assertEquals(0, abonoDao.listar(prestamo).size)
    }

    @Test
    fun cada_abono_dice_de_cuanto_fue_el_pago_general() {
        val reparto = pagoDao.previsualizar(idCliente, 500_000)
        pagoDao.registrar(idCliente, 500_000, reparto.lineas, "Nequi")

        assertEquals(
            "Abono general de $ 500.000 · aplicado aquí $ 150.000 · repartido en 2 deudas · " +
                "a favor $ 50.000 · Nequi",
            abonoDao.listar(mercado).single().nota
        )
        assertTrue(abonoDao.listar(prestamo).single().nota!!.contains("aplicado aquí $ 300.000"))
    }

    @Test
    fun lo_que_supera_el_pendiente_queda_a_favor() {
        val reparto = pagoDao.previsualizar(idCliente, 500_000)
        val idPago = pagoDao.registrar(idCliente, 500_000, reparto.lineas, null)

        assertEquals(50_000.0, pagoDao.obtener(idPago)!!.saldoFavor, 0.01)
        assertEquals(50_000, pagoDao.saldoFavor(idCliente))
        assertEquals(0, pagoDao.totalPendiente(idCliente))
        assertEquals(50_000.0, ClienteDao(helper).listarActivos().single().saldoFavor, 0.01)
    }

    @Test
    fun pago_sin_deudas_pendientes_queda_todo_a_favor() {
        deudaDao.anular(mercado)
        deudaDao.anular(prestamo)

        pagoDao.registrar(idCliente, 20_000, emptyList(), null)

        assertEquals(20_000, pagoDao.saldoFavor(idCliente))
    }

    @Test
    fun saldo_a_favor_se_aplica_a_una_deuda_nueva() {
        pagoDao.registrar(idCliente, 500_000, pagoDao.previsualizar(idCliente, 500_000).lineas, null)
        val nueva = deudaDao.insertar(idCliente, "Gaseosas", null, 30_000.0)

        val aplicado = pagoDao.aplicarSaldoFavorADeuda(idCliente, nueva)

        assertEquals(30_000, aplicado)
        assertEquals("pagada", deudaDao.obtener(nueva)!!.estado)
        assertEquals(20_000, pagoDao.saldoFavor(idCliente))
        assertTrue(abonoDao.listar(nueva).single().nota!!.startsWith("Saldo a favor del abono general de $ 500.000"))
    }

    @Test
    fun saldo_a_favor_que_no_alcanza_deja_la_deuda_pendiente() {
        pagoDao.registrar(idCliente, 500_000, pagoDao.previsualizar(idCliente, 500_000).lineas, null)
        val nueva = deudaDao.insertar(idCliente, "Arriendo", null, 80_000.0)

        assertEquals(50_000, pagoDao.aplicarSaldoFavorADeuda(idCliente, nueva))
        assertEquals(30_000.0, deudaDao.obtener(nueva)!!.saldo, 0.01)
        assertEquals(0, pagoDao.saldoFavor(idCliente))
        assertEquals(0, pagoDao.aplicarSaldoFavorADeuda(idCliente, nueva))
    }

    @Test
    fun registrar_usando_saldo_a_favor_lo_consume_primero() {
        // Deja $ 20.000 a favor: paga todo lo pendiente (450.000) con 470.000.
        pagoDao.registrar(idCliente, 470_000, pagoDao.previsualizar(idCliente, 470_000).lineas, null)
        val nueva = deudaDao.insertar(idCliente, "Arriendo", null, 100_000.0)

        // Llegan $ 90.000 nuevos y se usan los 20.000 a favor: se reparten 110.000 → solo 100.000 caben.
        val lineas = pagoDao.previsualizar(idCliente, 110_000).lineas
        val idPago = pagoDao.registrar(idCliente, 90_000, lineas, null, usarSaldoFavor = true)

        assertEquals("pagada", deudaDao.obtener(nueva)!!.estado)
        assertEquals(10_000, pagoDao.saldoFavor(idCliente)) // 20.000 + 90.000 - 100.000
        val abonos = abonoDao.listar(nueva)
        assertEquals(2, abonos.size)
        assertEquals(
            setOf(20_000.0, 80_000.0),
            abonos.map { it.valor }.toSet()
        )
        assertTrue(abonos.any { it.nota!!.startsWith("Saldo a favor del abono general de $ 470.000") })
        assertTrue(abonos.any { it.nota!!.contains("Abono general de $ 90.000 · aplicado aquí $ 80.000") })
        assertEquals(10_000.0, pagoDao.obtener(idPago)!!.saldoFavor, 0.01)
    }

    @Test
    fun registrar_solo_con_saldo_a_favor_no_crea_pago_nuevo() {
        pagoDao.registrar(idCliente, 470_000, pagoDao.previsualizar(idCliente, 470_000).lineas, null)
        val nueva = deudaDao.insertar(idCliente, "Arriendo", null, 15_000.0)
        val lineas = pagoDao.previsualizar(idCliente, 15_000).lineas

        val idPago = pagoDao.registrar(idCliente, 0, lineas, null, usarSaldoFavor = true)

        assertEquals(0, idPago)
        assertEquals(1, pagoDao.listar(idCliente).size)
        assertEquals("pagada", deudaDao.obtener(nueva)!!.estado)
        assertEquals(5_000, pagoDao.saldoFavor(idCliente))
    }

    @Test
    fun registrar_sin_valor_ni_montos_falla() {
        assertThrows(ErrorBd::class.java) { pagoDao.registrar(idCliente, 0, emptyList(), null) }
    }

    @Test
    fun anular_un_abono_del_pago_devuelve_su_monto_a_favor() {
        val idPago = pagoDao.registrar(idCliente, 200_000, pagoDao.previsualizar(idCliente, 200_000).lineas, null)

        abonoDao.anular(abonoDao.listar(mercado).single().id)

        assertEquals("activo", pagoDao.obtener(idPago)!!.estado)
        assertEquals(150_000, pagoDao.saldoFavor(idCliente))
    }

    @Test
    fun anular_la_deuda_devuelve_lo_del_pago_general_a_favor() {
        pagoDao.registrar(idCliente, 200_000, pagoDao.previsualizar(idCliente, 200_000).lineas, null)

        deudaDao.anular(prestamo)

        assertEquals(50_000, pagoDao.saldoFavor(idCliente))
        assertEquals(0, abonoDao.listar(prestamo).size)
    }

    @Test
    fun anular_el_pago_quita_su_saldo_a_favor() {
        val idPago = pagoDao.registrar(idCliente, 500_000, pagoDao.previsualizar(idCliente, 500_000).lineas, null)

        pagoDao.anular(idPago)

        assertEquals(0, pagoDao.saldoFavor(idCliente))
        assertEquals(450_000, pagoDao.totalPendiente(idCliente))
    }

    @Test
    fun registrar_rechaza_asignar_mas_que_lo_recibido() {
        val lineas = pagoDao.previsualizar(idCliente, 200_000).lineas

        assertThrows(ErrorBd::class.java) { pagoDao.registrar(idCliente, 100_000, lineas, null) }
        assertEquals(0, pagoDao.listar(idCliente).size)
    }

    @Test
    fun registrar_hace_rollback_si_falla_un_abono() {
        val lineas = pagoDao.previsualizar(idCliente, 200_000).lineas
        // Primera línea válida, segunda con monto mayor al saldo real: el trigger la rechaza.
        val dañadas = listOf(lineas[0], lineas[1].copy(saldo = 999_999, monto = 400_000))

        assertThrows(ErrorBd::class.java) { pagoDao.registrar(idCliente, 550_000, dañadas, null) }

        assertEquals(0, pagoDao.listar(idCliente).size)
        assertEquals(150_000.0, deudaDao.obtener(mercado)!!.saldo, 0.01)
        assertEquals(300_000.0, deudaDao.obtener(prestamo)!!.saldo, 0.01)
    }

    @Test
    fun anular_pago_restaura_saldos_y_estados() {
        val lineas = pagoDao.previsualizar(idCliente, 200_000).lineas
        val idPago = pagoDao.registrar(idCliente, 200_000, lineas, null)

        pagoDao.anular(idPago)

        assertEquals("anulado", pagoDao.obtener(idPago)!!.estado)
        assertEquals(0, pagoDao.listar(idCliente).size)
        assertEquals("pendiente", deudaDao.obtener(mercado)!!.estado)
        assertEquals(150_000.0, deudaDao.obtener(mercado)!!.saldo, 0.01)
        assertEquals(300_000.0, deudaDao.obtener(prestamo)!!.saldo, 0.01)
    }

    @Test
    fun deudas_anuladas_no_reciben_pago() {
        deudaDao.anular(mercado)

        val reparto = pagoDao.previsualizar(idCliente, 100_000)

        assertEquals(listOf(prestamo), reparto.lineas.map { it.idDeuda })
        assertEquals(300_000, pagoDao.totalPendiente(idCliente))
    }
}
