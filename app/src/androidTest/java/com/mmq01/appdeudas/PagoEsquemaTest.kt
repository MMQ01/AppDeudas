package com.mmq01.appdeudas

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmq01.appdeudas.Clases.Cliente
import com.mmq01.appdeudas.DBHelper.ClienteDao
import com.mmq01.appdeudas.DBHelper.DeudaDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PagoEsquemaTest {

    private lateinit var contexto: Context
    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var deudaDao: DeudaDao

    private val idCliente = "573001234567"

    @Before
    fun setUp() {
        contexto = InstrumentationRegistry.getInstrumentation().targetContext
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
        helper = DeudasDatabaseHelper(contexto)
        ClienteDao(helper).insertar(Cliente(idCliente = idCliente, nombre = "Ana"))
        deudaDao = DeudaDao(helper)
    }

    @After
    fun tearDown() {
        helper.close()
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
    }

    private fun crearPago(valor: Double = 500_000.0): Long {
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO pago(idCliente, valor, nota) VALUES (?, ?, 'general')",
            arrayOf(idCliente, valor)
        )
        return db.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong()
    }

    private fun abonarEnPago(idDeuda: Long, valor: Double, idPago: Long) {
        helper.writableDatabase.execSQL(
            "INSERT INTO abono(idDeuda, valor, idPago) VALUES (?, ?, ?)",
            arrayOf(idDeuda, valor, idPago)
        )
    }

    private fun contar(tabla: String): Long =
        helper.readableDatabase.compileStatement("SELECT COUNT(*) FROM $tabla").simpleQueryForLong()

    @Test
    fun vista_pago_deriva_aplicado_saldo_a_favor_y_numero_de_deudas() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        val d2 = deudaDao.insertar(idCliente, "Préstamo", null, 300_000.0)
        val idPago = crearPago(250_000.0)
        abonarEnPago(d1, 150_000.0, idPago)
        abonarEnPago(d2, 50_000.0, idPago)

        helper.readableDatabase.rawQuery(
            "SELECT valor, aplicado, saldoFavor, numDeudas FROM vw_pago WHERE id = ?",
            arrayOf(idPago.toString())
        ).use { c ->
            c.moveToFirst()
            assertEquals(250_000.0, c.getDouble(0), 0.01)
            assertEquals(200_000.0, c.getDouble(1), 0.01)
            assertEquals(50_000.0, c.getDouble(2), 0.01)
            assertEquals(2, c.getInt(3))
        }
        assertEquals("pagada", deudaDao.obtener(d1)!!.estado)
        assertEquals(250_000.0, deudaDao.obtener(d2)!!.saldo, 0.01)
    }

    @Test
    fun anular_pago_restaura_los_saldos_sin_borrar() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        val d2 = deudaDao.insertar(idCliente, "Préstamo", null, 300_000.0)
        val idPago = crearPago()
        abonarEnPago(d1, 150_000.0, idPago)
        abonarEnPago(d2, 50_000.0, idPago)

        helper.writableDatabase.execSQL("UPDATE pago SET estado = 'anulado' WHERE id = ?", arrayOf(idPago))

        assertEquals(2, contar("abono"))
        assertEquals(0, contar("abono WHERE estado = 'activo'"))
        assertEquals(1, contar("pago WHERE estado = 'anulado'"))
        assertEquals("pendiente", deudaDao.obtener(d1)!!.estado)
        assertEquals(150_000.0, deudaDao.obtener(d1)!!.saldo, 0.01)
        assertEquals(300_000.0, deudaDao.obtener(d2)!!.saldo, 0.01)
    }

    @Test
    fun pago_sin_abonos_activos_sigue_activo_con_todo_a_favor() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        val idPago = crearPago(100_000.0)
        abonarEnPago(d1, 100_000.0, idPago)

        deudaDao.reabrir(d1)

        assertEquals(1, contar("pago WHERE estado = 'activo'"))
        assertEquals(1, contar("abono WHERE estado = 'anulado'"))
        assertEquals(150_000.0, deudaDao.obtener(d1)!!.saldo, 0.01)
        helper.readableDatabase.rawQuery(
            "SELECT saldoFavor FROM vw_pago WHERE id = ?", arrayOf(idPago.toString())
        ).use { c ->
            c.moveToFirst()
            assertEquals(100_000.0, c.getDouble(0), 0.01)
        }
    }

    @Test
    fun abonos_de_un_pago_no_pasan_de_su_valor() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        val d2 = deudaDao.insertar(idCliente, "Préstamo", null, 300_000.0)
        val idPago = crearPago(200_000.0)
        abonarEnPago(d1, 150_000.0, idPago)

        assertThrows(SQLiteConstraintException::class.java) { abonarEnPago(d2, 60_000.0, idPago) }
        abonarEnPago(d2, 50_000.0, idPago)
    }

    @Test
    fun no_se_abona_desde_un_pago_anulado() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        val idPago = crearPago()
        helper.writableDatabase.execSQL("UPDATE pago SET estado = 'anulado' WHERE id = ?", arrayOf(idPago))

        assertThrows(SQLiteConstraintException::class.java) { abonarEnPago(d1, 1_000.0, idPago) }
    }

    @Test
    fun abono_anulado_no_se_reactiva() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        abonarEnPago(d1, 100_000.0, crearPago())
        deudaDao.reabrir(d1)

        assertThrows(SQLiteConstraintException::class.java) {
            helper.writableDatabase.execSQL("UPDATE abono SET estado = 'activo'")
        }
        assertEquals(150_000.0, deudaDao.obtener(d1)!!.saldo, 0.01)
    }

    @Test
    fun anular_un_abono_deja_el_pago_con_los_demas() {
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        val d2 = deudaDao.insertar(idCliente, "Préstamo", null, 300_000.0)
        val idPago = crearPago()
        abonarEnPago(d1, 100_000.0, idPago)
        abonarEnPago(d2, 50_000.0, idPago)

        deudaDao.reabrir(d1)

        assertEquals(1, contar("pago WHERE estado = 'activo'"))
        helper.readableDatabase.rawQuery(
            "SELECT aplicado, numDeudas FROM vw_pago WHERE id = ?", arrayOf(idPago.toString())
        ).use { c ->
            c.moveToFirst()
            assertEquals(50_000.0, c.getDouble(0), 0.01)
            assertEquals(1, c.getInt(1))
        }
    }

    @Test
    fun inactivar_cliente_conserva_su_historial() {
        val clienteDao = ClienteDao(helper)
        val d1 = deudaDao.insertar(idCliente, "Mercado", null, 150_000.0)
        abonarEnPago(d1, 100_000.0, crearPago())

        clienteDao.inactivar(idCliente)

        assertEquals("inactivo", clienteDao.obtener(idCliente)!!.estado)
        assertTrue(clienteDao.listarActivos().isEmpty())
        assertEquals(1, contar("pago"))
        assertEquals(1, contar("abono"))
        assertEquals(50_000.0, deudaDao.obtener(d1)!!.saldo, 0.01)

        clienteDao.reactivar(Cliente(idCliente = idCliente, nombre = "Ana María"))

        val resumen = clienteDao.listarActivos().single()
        assertEquals("Ana María", resumen.nombre)
        assertEquals(50_000.0, resumen.saldoPendiente, 0.01)
    }
}
