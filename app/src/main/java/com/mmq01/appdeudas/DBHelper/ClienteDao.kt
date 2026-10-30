package com.mmq01.appdeudas.DBHelper

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import com.mmq01.appdeudas.Clases.Cliente
import com.mmq01.appdeudas.Clases.ResumenCliente

class ClienteDao(private val helper: DeudasDatabaseHelper) {

    fun insertar(cliente: Cliente) {
        val valores = ContentValues().apply {
            put("idCliente", cliente.idCliente)
            put("nombre", cliente.nombre)
            put("telefonoOriginal", cliente.telefonoOriginal)
            put("contactoUri", cliente.contactoUri)
            put("origen", cliente.origen)
            put("estado", cliente.estado)
        }
        try {
            helper.writableDatabase.insertOrThrow("cliente", null, valores)
        } catch (e: SQLiteException) {
            throw ErrorBd("Ese cliente ya existe")
        }
    }

    fun existe(idCliente: String): Boolean {
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM cliente WHERE idCliente = ? LIMIT 1",
            arrayOf(idCliente)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun obtener(idCliente: String): Cliente? {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM cliente WHERE idCliente = ? LIMIT 1",
            arrayOf(idCliente)
        ).use { cursor ->
            return if (cursor.moveToFirst()) leerCliente(cursor) else null
        }
    }

    fun listarActivos(): List<ResumenCliente> {
        val lista = mutableListOf<ResumenCliente>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM vw_resumen_cliente WHERE estado = 'activo' ORDER BY nombre COLLATE NOCASE",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) lista.add(leerResumen(cursor))
        }
        return lista
    }

    fun actualizarNombre(idCliente: String, nombre: String) {
        val valores = ContentValues().apply { put("nombre", nombre) }
        helper.writableDatabase.update("cliente", valores, "idCliente = ?", arrayOf(idCliente))
    }

    fun actualizarNombreDesdeContacto(idCliente: String, nombre: String): Boolean {
        val valores = ContentValues().apply { put("nombre", nombre) }
        return helper.writableDatabase.update(
            "cliente",
            valores,
            "idCliente = ? AND nombre != ?",
            arrayOf(idCliente, nombre)
        ) > 0
    }

    /** No borra: marca el cliente 'inactivo' y conserva sus deudas y abonos. */
    fun inactivar(idCliente: String) {
        val valores = ContentValues().apply { put("estado", "inactivo") }
        helper.writableDatabase.update("cliente", valores, "idCliente = ?", arrayOf(idCliente))
    }

    /** Vuelve a activar un cliente inactivo con los datos nuevos; su historial reaparece. */
    fun reactivar(cliente: Cliente) {
        val valores = ContentValues().apply {
            put("nombre", cliente.nombre)
            put("telefonoOriginal", cliente.telefonoOriginal)
            put("contactoUri", cliente.contactoUri)
            put("origen", cliente.origen)
            put("estado", "activo")
        }
        helper.writableDatabase.update("cliente", valores, "idCliente = ?", arrayOf(cliente.idCliente))
    }

    private fun leerCliente(cursor: Cursor): Cliente = Cliente(
        idCliente = cursor.getString(cursor.getColumnIndexOrThrow("idCliente")),
        nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")),
        telefonoOriginal = cursor.getString(cursor.getColumnIndexOrThrow("telefonoOriginal")),
        contactoUri = cursor.getString(cursor.getColumnIndexOrThrow("contactoUri")),
        origen = cursor.getString(cursor.getColumnIndexOrThrow("origen")),
        estado = cursor.getString(cursor.getColumnIndexOrThrow("estado")),
        fechaCreacion = cursor.getString(cursor.getColumnIndexOrThrow("fechaCreacion")),
        fechaModificacion = cursor.getString(cursor.getColumnIndexOrThrow("fechaModificacion"))
    )

    private fun leerResumen(cursor: Cursor): ResumenCliente = ResumenCliente(
        idCliente = cursor.getString(cursor.getColumnIndexOrThrow("idCliente")),
        nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")),
        telefonoOriginal = cursor.getString(cursor.getColumnIndexOrThrow("telefonoOriginal")),
        contactoUri = cursor.getString(cursor.getColumnIndexOrThrow("contactoUri")),
        origen = cursor.getString(cursor.getColumnIndexOrThrow("origen")),
        estado = cursor.getString(cursor.getColumnIndexOrThrow("estado")),
        totalDeudas = cursor.getInt(cursor.getColumnIndexOrThrow("totalDeudas")),
        deudasPendientes = cursor.getInt(cursor.getColumnIndexOrThrow("deudasPendientes")),
        valorTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("valorTotal")),
        saldoPendiente = cursor.getDouble(cursor.getColumnIndexOrThrow("saldoPendiente")),
        totalPagado = cursor.getDouble(cursor.getColumnIndexOrThrow("totalPagado")),
        saldoFavor = cursor.getDouble(cursor.getColumnIndexOrThrow("saldoFavor"))
    )
}
