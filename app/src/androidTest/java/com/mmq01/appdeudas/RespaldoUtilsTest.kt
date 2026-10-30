package com.mmq01.appdeudas

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmq01.appdeudas.Clases.Cliente
import com.mmq01.appdeudas.Clases.Respaldo
import com.mmq01.appdeudas.DBHelper.ClienteDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.Utils.RespaldoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RespaldoUtilsTest {

    private lateinit var contexto: Context
    private lateinit var helper: DeudasDatabaseHelper

    private val idCliente = "573001234567"

    @Before
    fun setUp() {
        contexto = InstrumentationRegistry.getInstrumentation().targetContext
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
        RespaldoUtils.carpeta(contexto).deleteRecursively()
        helper = DeudasDatabaseHelper(contexto)
    }

    @After
    fun tearDown() {
        helper.close()
        contexto.deleteDatabase(DeudasDatabaseHelper.DATABASE_NAME)
        RespaldoUtils.carpeta(contexto).deleteRecursively()
    }

    @Test
    fun crea_respaldo_de_la_misma_version() {
        ClienteDao(helper).insertar(Cliente(idCliente = idCliente, nombre = "Ana"))

        val respaldo = RespaldoUtils.crear(contexto)

        assertTrue(respaldo.archivo.exists())
        assertTrue(respaldo.tamano > 0)
        assertEquals(
            DeudasDatabaseHelper.DATABASE_VERSION,
            RespaldoUtils.versionBase(respaldo.archivo)
        )
    }

    @Test
    fun restaurar_devuelve_los_datos_del_respaldo() {
        val clienteDao = ClienteDao(helper)
        clienteDao.insertar(Cliente(idCliente = idCliente, nombre = "Ana"))
        val respaldo = RespaldoUtils.crear(contexto)

        clienteDao.actualizarNombre(idCliente, "Cambiado")
        helper.close()

        RespaldoUtils.restaurar(contexto, respaldo)

        val otro = DeudasDatabaseHelper(contexto)
        try {
            assertEquals("Ana", ClienteDao(otro).obtener(idCliente)?.nombre)
        } finally {
            otro.close()
        }
        helper = DeudasDatabaseHelper(contexto)
    }

    @Test
    fun rechaza_respaldo_de_otra_version() {
        val directorio = RespaldoUtils.carpeta(contexto)
        directorio.mkdirs()
        val archivo = File(directorio, "deudas-20200101-000000.db")
        SQLiteDatabase.openOrCreateDatabase(archivo, null).use { it.version = 1 }

        assertThrows(ErrorBd::class.java) {
            RespaldoUtils.restaurar(contexto, Respaldo(archivo, archivo.lastModified()))
        }
    }
}
