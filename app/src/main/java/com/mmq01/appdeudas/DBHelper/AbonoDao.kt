package com.mmq01.appdeudas.DBHelper

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import com.mmq01.appdeudas.Clases.Abono

class AbonoDao(private val helper: DeudasDatabaseHelper) {

    fun insertar(idDeuda: Long, valor: Double, tipo: String = "abono", nota: String? = null) {
        val valores = ContentValues().apply {
            put("idDeuda", idDeuda)
            put("valor", valor)
            put("tipo", tipo)
            put("nota", nota)
        }
        try {
            helper.writableDatabase.insertOrThrow("abono", null, valores)
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        }
    }

    fun listar(idDeuda: Long): List<Abono> {
        val lista = mutableListOf<Abono>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM abono WHERE idDeuda = ? AND estado = 'activo' ORDER BY fecha DESC, id DESC",
            arrayOf(idDeuda.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) lista.add(leerAbono(cursor))
        }
        return lista
    }

    /** No borra: marca el abono 'anulado'; los triggers recalculan saldo y estado. */
    fun anular(id: Long) {
        try {
            helper.writableDatabase.execSQL(
                "UPDATE abono SET estado = 'anulado', fechaAnulacion = datetime('now') " +
                    "WHERE id = ? AND estado = 'activo'",
                arrayOf(id)
            )
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        }
    }

    private fun leerAbono(cursor: Cursor): Abono = Abono(
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
        idDeuda = cursor.getLong(cursor.getColumnIndexOrThrow("idDeuda")),
        valor = cursor.getDouble(cursor.getColumnIndexOrThrow("valor")),
        tipo = cursor.getString(cursor.getColumnIndexOrThrow("tipo")),
        fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
        nota = cursor.getString(cursor.getColumnIndexOrThrow("nota")),
        idPago = cursor.getColumnIndexOrThrow("idPago").let { i ->
            if (cursor.isNull(i)) null else cursor.getLong(i)
        },
        estado = cursor.getString(cursor.getColumnIndexOrThrow("estado"))
    )

    private fun traducir(e: SQLiteException): String {
        val mensaje = e.message ?: ""
        return when {
            mensaje.contains("anulada", ignoreCase = true) -> "La deuda está anulada"
            mensaje.contains("abono está anulado", ignoreCase = true) -> "El abono está anulado"
            mensaje.contains("supera el saldo", ignoreCase = true) -> "El abono supera el saldo de la deuda"
            else -> "No se pudo completar la operación"
        }
    }
}
