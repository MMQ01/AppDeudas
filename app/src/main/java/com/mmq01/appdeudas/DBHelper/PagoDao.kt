package com.mmq01.appdeudas.DBHelper

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.mmq01.appdeudas.Clases.DeudaPendiente
import com.mmq01.appdeudas.Clases.FuenteFavor
import com.mmq01.appdeudas.Clases.LineaReparto
import com.mmq01.appdeudas.Clases.Pago
import com.mmq01.appdeudas.Clases.Reparto
import com.mmq01.appdeudas.Utils.RepartoUtils
import kotlin.math.roundToLong

class PagoDao(private val helper: DeudasDatabaseHelper) {

    /** Suma de saldos pendientes del cliente, en pesos enteros. */
    fun totalPendiente(idCliente: String): Long =
        pendientes(idCliente).sumOf { it.saldo }

    /** Reparto sugerido (más antigua primero) para un pago de `valor`. No escribe nada. */
    fun previsualizar(idCliente: String, valor: Long): Reparto =
        RepartoUtils.fifo(pendientes(idCliente), valor)

    /** Saldo a favor del cliente (suma de lo no aplicado de sus pagos activos), en pesos. */
    fun saldoFavor(idCliente: String): Long =
        fuentesFavor(idCliente).sumOf { it.disponible }

    /**
     * Registra un abono general en una sola transacción: si falla un abono (p. ej. el trigger
     * rechaza un monto), no queda nada guardado. `valorRecibido` es el dinero nuevo que entregó
     * el cliente (puede ser 0 si solo se usa saldo a favor). Con `usarSaldoFavor`, el saldo a
     * favor existente se suma a lo repartible y se consume primero (pago más antiguo primero).
     * Lo que no se asigna a deudas queda a favor. Cada abono del pago nuevo lleva en la nota de
     * cuánto fue el pago. Solo se insertan pago y abonos; saldos y estados los recalculan los
     * triggers. Devuelve el id del pago nuevo (0 si no se creó ninguno).
     */
    fun registrar(
        idCliente: String,
        valorRecibido: Long,
        lineas: List<LineaReparto>,
        nota: String?,
        usarSaldoFavor: Boolean = false
    ): Long {
        val aplicar = lineas.filter { it.monto > 0 }
        val fuentes = if (usarSaldoFavor) fuentesFavor(idCliente) else emptyList()
        val disponible = valorRecibido + fuentes.sumOf { it.disponible }

        if (valorRecibido < 0) throw ErrorBd("El valor no puede ser negativo")
        RepartoUtils.validar(aplicar, disponible)?.let { throw ErrorBd(it) }
        if (valorRecibido == 0L && aplicar.isEmpty()) throw ErrorBd("Escribe un valor o asigna montos")

        // Lo que cubre el saldo a favor existente y lo que cubre el dinero nuevo, por deuda.
        val deFavor = RepartoUtils.repartirFavor(fuentes, aplicar)
        val favorPorDeuda = deFavor.groupBy { it.idDeuda }.mapValues { (_, v) -> v.sumOf { it.monto } }
        val nuevas = aplicar.mapNotNull { linea ->
            val monto = linea.monto - (favorPorDeuda[linea.idDeuda] ?: 0)
            if (monto > 0) linea.copy(monto = monto) else null
        }
        val aFavor = valorRecibido - nuevas.sumOf { it.monto }
        val notaUsuario = nota?.trim()?.ifBlank { null }

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            deFavor.forEach { a ->
                val notaAbono = RepartoUtils.notaSaldoFavor(a.fuente.valorPago, a.fuente.fecha)
                insertarAbono(db, a.idDeuda, a.monto, notaAbono, a.fuente.idPago)
            }
            var idPago = 0L
            if (valorRecibido > 0) {
                idPago = db.insertOrThrow("pago", null, ContentValues().apply {
                    put("idCliente", idCliente)
                    put("valor", valorRecibido.toDouble())
                    put("nota", notaUsuario)
                })
                nuevas.forEach { linea ->
                    val notaAbono = RepartoUtils.notaAbonoGeneral(
                        valorRecibido, linea.monto, nuevas.size, aFavor, notaUsuario
                    )
                    insertarAbono(db, linea.idDeuda, linea.monto, notaAbono, idPago)
                }
            }
            db.setTransactionSuccessful()
            return idPago
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Aplica saldo a favor a las líneas (monto deseado por deuda), tomando del pago más
     * antiguo primero. Si no alcanza, aplica lo que haya. Devuelve el total aplicado.
     */
    fun aplicarSaldoFavor(idCliente: String, lineas: List<LineaReparto>): Long {
        val aplicaciones = RepartoUtils.repartirFavor(fuentesFavor(idCliente), lineas)
        if (aplicaciones.isEmpty()) return 0

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            aplicaciones.forEach { a ->
                val notaAbono = RepartoUtils.notaSaldoFavor(a.fuente.valorPago, a.fuente.fecha)
                insertarAbono(db, a.idDeuda, a.monto, notaAbono, a.fuente.idPago)
            }
            db.setTransactionSuccessful()
            return aplicaciones.sumOf { it.monto }
        } catch (e: SQLiteException) {
            throw ErrorBd(traducir(e))
        } finally {
            db.endTransaction()
        }
    }

    /** Aplica a una deuda todo el saldo a favor que quepa en su saldo. Devuelve lo aplicado. */
    fun aplicarSaldoFavorADeuda(idCliente: String, idDeuda: Long): Long {
        val deuda = pendientes(idCliente).firstOrNull { it.id == idDeuda } ?: return 0
        val linea = LineaReparto(deuda.id, deuda.nombre, deuda.fechaCreacion, deuda.saldo, deuda.saldo)
        return aplicarSaldoFavor(idCliente, listOf(linea))
    }

    fun listar(idCliente: String): List<Pago> {
        val lista = mutableListOf<Pago>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM vw_pago WHERE idCliente = ? AND estado = 'activo' ORDER BY fecha DESC, id DESC",
            arrayOf(idCliente)
        ).use { cursor ->
            while (cursor.moveToNext()) lista.add(leerPago(cursor))
        }
        return lista
    }

    fun obtener(id: Long): Pago? {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM vw_pago WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) leerPago(cursor) else null
        }
    }

    /** No borra: anula el pago; trg_pago_anular anula sus abonos y se restauran los saldos. */
    fun anular(id: Long) {
        helper.writableDatabase.execSQL(
            "UPDATE pago SET estado = 'anulado', fechaAnulacion = datetime('now') " +
                "WHERE id = ? AND estado = 'activo'",
            arrayOf(id)
        )
    }

    private fun insertarAbono(db: SQLiteDatabase, idDeuda: Long, monto: Long, nota: String, idPago: Long) {
        db.insertOrThrow("abono", null, ContentValues().apply {
            put("idDeuda", idDeuda)
            put("valor", monto.toDouble())
            put("tipo", "abono")
            put("nota", nota)
            put("idPago", idPago)
        })
    }

    private fun fuentesFavor(idCliente: String): List<FuenteFavor> {
        val lista = mutableListOf<FuenteFavor>()
        helper.readableDatabase.rawQuery(
            "SELECT id, valor, fecha, saldoFavor FROM vw_pago " +
                "WHERE idCliente = ? AND estado = 'activo' AND saldoFavor > 0.0001 " +
                "ORDER BY fecha ASC, id ASC",
            arrayOf(idCliente)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add(
                    FuenteFavor(
                        idPago = cursor.getLong(0),
                        valorPago = cursor.getDouble(1).roundToLong(),
                        fecha = cursor.getString(2),
                        disponible = cursor.getDouble(3).roundToLong()
                    )
                )
            }
        }
        return lista
    }

    private fun pendientes(idCliente: String): List<DeudaPendiente> {
        val lista = mutableListOf<DeudaPendiente>()
        helper.readableDatabase.rawQuery(
            "SELECT id, nombre, fechaCreacion, saldo FROM deuda " +
                "WHERE idCliente = ? AND estado = 'pendiente' AND saldo > 0 " +
                "ORDER BY fechaCreacion ASC, id ASC",
            arrayOf(idCliente)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add(
                    DeudaPendiente(
                        id = cursor.getLong(0),
                        nombre = cursor.getString(1),
                        fechaCreacion = cursor.getString(2),
                        saldo = cursor.getDouble(3).roundToLong()
                    )
                )
            }
        }
        return lista
    }

    private fun leerPago(cursor: Cursor): Pago = Pago(
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
        idCliente = cursor.getString(cursor.getColumnIndexOrThrow("idCliente")),
        valor = cursor.getDouble(cursor.getColumnIndexOrThrow("valor")),
        fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
        nota = cursor.getString(cursor.getColumnIndexOrThrow("nota")),
        aplicado = cursor.getDouble(cursor.getColumnIndexOrThrow("aplicado")),
        saldoFavor = cursor.getDouble(cursor.getColumnIndexOrThrow("saldoFavor")),
        numDeudas = cursor.getInt(cursor.getColumnIndexOrThrow("numDeudas")),
        estado = cursor.getString(cursor.getColumnIndexOrThrow("estado"))
    )

    private fun traducir(e: SQLiteException): String {
        val mensaje = e.message ?: ""
        return when {
            mensaje.contains("anulada", ignoreCase = true) -> "Una de las deudas está anulada"
            mensaje.contains("pago está anulado", ignoreCase = true) -> "El pago está anulado"
            mensaje.contains("valor del pago", ignoreCase = true) ->
                "Se asignó más que el valor del pago"
            mensaje.contains("supera el saldo", ignoreCase = true) ->
                "Un monto supera el saldo de su deuda"
            else -> "No se pudo registrar el pago"
        }
    }
}
