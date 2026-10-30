package com.mmq01.appdeudas.DBHelper

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import com.mmq01.appdeudas.Clases.DeudaDetalle

class DeudaDao(private val helper: DeudasDatabaseHelper) {

    fun insertar(idCliente: String, nombre: String, descripcion: String?, valor: Double): Long {
        val valores = ContentValues().apply {
            put("idCliente", idCliente)
            put("nombre", nombre)
            put("descripcion", descripcion)
            put("valor", valor)
            put("saldo", valor)
        }
        return try {
            helper.writableDatabase.insertOrThrow("deuda", null, valores)
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        }
    }

    fun actualizar(id: Long, nombre: String, descripcion: String?, valor: Double) {
        val valores = ContentValues().apply {
            put("nombre", nombre)
            put("descripcion", descripcion)
            put("valor", valor)
        }
        try {
            helper.writableDatabase.update(
                "deuda",
                valores,
                "id = ? AND estado != 'anulada'",
                arrayOf(id.toString())
            )
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        }
    }

    fun obtener(id: Long): DeudaDetalle? {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM vw_deuda_detalle WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) leerDeuda(cursor) else null
        }
    }

    fun listar(idCliente: String, estado: String?): List<DeudaDetalle> {
        val lista = mutableListOf<DeudaDetalle>()
        val sql = if (estado == null) {
            "SELECT * FROM vw_deuda_detalle WHERE idCliente = ? ORDER BY fechaCreacion DESC, id DESC"
        } else {
            "SELECT * FROM vw_deuda_detalle WHERE idCliente = ? AND estado = ? ORDER BY fechaCreacion DESC, id DESC"
        }
        val argumentos = if (estado == null) arrayOf(idCliente) else arrayOf(idCliente, estado)
        helper.readableDatabase.rawQuery(sql, argumentos).use { cursor ->
            while (cursor.moveToNext()) lista.add(leerDeuda(cursor))
        }
        return lista
    }

    fun totalPendiente(idCliente: String): Double {
        helper.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(saldo), 0) FROM deuda WHERE idCliente = ? AND estado = 'pendiente'",
            arrayOf(idCliente)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
    }

    fun marcarPagada(id: Long) {
        try {
            helper.writableDatabase.execSQL(
                "INSERT INTO abono(idDeuda, valor, tipo, nota) " +
                    "SELECT id, saldo, 'cierre', 'Marcada como pagada' FROM deuda WHERE id = ? AND saldo > 0",
                arrayOf(id)
            )
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        }
    }

    /** Anula (no borra) el último abono activo; los triggers dejan la deuda pendiente. */
    fun reabrir(id: Long) {
        helper.writableDatabase.execSQL(
            "UPDATE abono SET estado = 'anulado', fechaAnulacion = datetime('now') WHERE id = " +
                "(SELECT id FROM abono WHERE idDeuda = ? AND estado = 'activo' " +
                "ORDER BY fecha DESC, id DESC LIMIT 1)",
            arrayOf(id)
        )
    }

    fun anular(id: Long) {
        helper.writableDatabase.execSQL(
            "UPDATE deuda SET estado = 'anulada', fechaAnulacion = datetime('now') " +
                "WHERE id = ? AND estado != 'anulada'",
            arrayOf(id)
        )
    }

    private fun leerDeuda(cursor: Cursor): DeudaDetalle = DeudaDetalle(
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
        idCliente = cursor.getString(cursor.getColumnIndexOrThrow("idCliente")),
        nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")),
        descripcion = cursor.getString(cursor.getColumnIndexOrThrow("descripcion")),
        valor = cursor.getDouble(cursor.getColumnIndexOrThrow("valor")),
        saldo = cursor.getDouble(cursor.getColumnIndexOrThrow("saldo")),
        estado = cursor.getString(cursor.getColumnIndexOrThrow("estado")),
        fechaCreacion = cursor.getString(cursor.getColumnIndexOrThrow("fechaCreacion")),
        fechaPago = cursor.getString(cursor.getColumnIndexOrThrow("fechaPago")),
        fechaAnulacion = cursor.getString(cursor.getColumnIndexOrThrow("fechaAnulacion")),
        fechaModificacion = cursor.getString(cursor.getColumnIndexOrThrow("fechaModificacion")),
        totalAbonado = cursor.getDouble(cursor.getColumnIndexOrThrow("totalAbonado")),
        numAbonos = cursor.getInt(cursor.getColumnIndexOrThrow("numAbonos"))
    )

    private fun traducir(e: SQLiteException): String {
        val mensaje = e.message ?: ""
        return when {
            mensaje.contains("anulada", ignoreCase = true) -> "La deuda está anulada"
            mensaje.contains("supera el saldo", ignoreCase = true) -> "El abono supera el saldo de la deuda"
            mensaje.contains("menor que lo ya abonado", ignoreCase = true) ->
                "El valor no puede ser menor que lo ya abonado"
            else -> "No se pudo completar la operación"
        }
    }
}
