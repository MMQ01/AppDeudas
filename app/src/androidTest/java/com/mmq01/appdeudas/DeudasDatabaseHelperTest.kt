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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeudasDatabaseHelperTest {

    private lateinit var contexto: Context
    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var clienteDao: ClienteDao
    private lateinit var deudaDao: DeudaDao
    private lateinit var abonoDao: AbonoDao

    private val idCliente = "573001234567"

    @Before
    fun setUp() {
        contexto = InstrumentationRegistry.getInstrumentation().targetContext
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
        helper = DeudasDatabaseHelper(contexto)
        clienteDao = ClienteDao(helper)
        deudaDao = DeudaDao(helper)
        abonoDao = AbonoDao(helper)
        clienteDao.insertar(Cliente(idCliente = idCliente, nombre = "Ana"))
    }

    @After
    fun tearDown() {
        helper.close()
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
    }

    private fun crearDeuda(valor: Double = 1_000_000.0): Long =
        deudaDao.insertar(idCliente, "Préstamo", null, valor)

    @Test
    fun abono_parcial_recalcula_saldo_y_estado() {
        val id = crearDeuda()
        abonoDao.insertar(id, 400_000.0)

        val deuda = deudaDao.obtener(id)!!
        assertEquals(600_000.0, deuda.saldo, 0.01)
        assertEquals("pendiente", deuda.estado)
        assertEquals(400_000.0, deuda.totalAbonado, 0.01)
    }

    @Test
    fun abono_total_marca_pagada() {
        val id = crearDeuda()
        abonoDao.insertar(id, 1_000_000.0)

        val deuda = deudaDao.obtener(id)!!
        assertEquals(0.0, deuda.saldo, 0.01)
        assertEquals("pagada", deuda.estado)
        assertNotNull(deuda.fechaPago)
    }

    @Test
    fun abono_que_supera_el_saldo_falla() {
        val id = crearDeuda()
        assertThrows(ErrorBd::class.java) { abonoDao.insertar(id, 1_200_000.0) }
        assertEquals("pendiente", deudaDao.obtener(id)!!.estado)
    }

    @Test
    fun marcar_pagada_genera_abono_de_cierre() {
        val id = crearDeuda()
        deudaDao.marcarPagada(id)

        val deuda = deudaDao.obtener(id)!!
        assertEquals("pagada", deuda.estado)
        assertEquals(0.0, deuda.saldo, 0.01)
        assertEquals(1_000_000.0, deuda.totalAbonado, 0.01)
    }

    @Test
    fun reabrir_elimina_el_ultimo_abono() {
        val id = crearDeuda()
        abonoDao.insertar(id, 1_000_000.0)
        assertEquals("pagada", deudaDao.obtener(id)!!.estado)

        deudaDao.reabrir(id)

        val deuda = deudaDao.obtener(id)!!
        assertEquals("pendiente", deuda.estado)
        assertEquals(1_000_000.0, deuda.saldo, 0.01)
    }

    @Test
    fun editar_valor_por_debajo_de_lo_abonado_falla() {
        val id = crearDeuda()
        abonoDao.insertar(id, 400_000.0)

        assertThrows(ErrorBd::class.java) {
            deudaDao.actualizar(id, "Préstamo", null, 300_000.0)
        }
    }

    @Test
    fun deuda_anulada_no_cuenta_en_totales() {
        val id = crearDeuda()
        abonoDao.insertar(id, 100_000.0)
        deudaDao.anular(id)

        assertEquals("anulada", deudaDao.obtener(id)!!.estado)
        assertEquals(0.0, deudaDao.totalPendiente(idCliente), 0.01)
    }

    @Test
    fun no_permite_abonar_una_deuda_anulada() {
        val id = crearDeuda()
        deudaDao.anular(id)

        assertThrows(ErrorBd::class.java) { abonoDao.insertar(id, 1_000.0) }
    }
}
