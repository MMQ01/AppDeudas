package com.mmq01.appdeudas.DBHelper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

class DeudasDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val contexto = context.applicationContext

    companion object {
        const val DATABASE_NAME = "deudas.db"
        const val DATABASE_VERSION = 4

        fun dividirSentencias(script: String): List<String> {
            val sentencias = mutableListOf<String>()
            val actual = StringBuilder()
            var bloques = 0
            var cases = 0
            var i = 0

            while (i < script.length) {
                val c = script[i]
                when {
                    c == '-' && i + 1 < script.length && script[i + 1] == '-' -> {
                        val fin = script.indexOf('\n', i)
                        val hasta = if (fin == -1) script.length else fin + 1
                        actual.append(script, i, hasta)
                        i = hasta
                    }

                    c == '/' && i + 1 < script.length && script[i + 1] == '*' -> {
                        val fin = script.indexOf("*/", i + 2)
                        val hasta = if (fin == -1) script.length else fin + 2
                        actual.append(script, i, hasta)
                        i = hasta
                    }

                    c == '\'' -> {
                        val inicio = i
                        i++
                        while (i < script.length) {
                            if (script[i] == '\'') {
                                if (i + 1 < script.length && script[i + 1] == '\'') {
                                    i += 2
                                    continue
                                }
                                i++
                                break
                            }
                            i++
                        }
                        actual.append(script, inicio, i)
                    }

                    c.isLetter() -> {
                        val inicio = i
                        while (i < script.length && (script[i].isLetter() || script[i] == '_')) i++
                        when (script.substring(inicio, i).uppercase(Locale.ROOT)) {
                            "BEGIN" -> bloques++
                            "CASE" -> cases++
                            "END" -> if (cases > 0) cases-- else if (bloques > 0) bloques--
                        }
                        actual.append(script, inicio, i)
                    }

                    c == ';' && bloques == 0 && cases == 0 -> {
                        val sentencia = actual.toString().trim()
                        if (sentencia.isNotEmpty()) sentencias.add(sentencia)
                        actual.setLength(0)
                        i++
                    }

                    else -> {
                        actual.append(c)
                        i++
                    }
                }
            }

            val resto = actual.toString().trim()
            if (resto.isNotEmpty()) sentencias.add(resto)
            return sentencias
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        val script = contexto.assets.open("schema.sql").bufferedReader().use { it.readText() }
        dividirSentencias(script).forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP VIEW IF EXISTS vw_pago")
        db.execSQL("DROP VIEW IF EXISTS vw_deuda_detalle")
        db.execSQL("DROP VIEW IF EXISTS vw_resumen_cliente")
        db.execSQL("DROP TABLE IF EXISTS abono")
        db.execSQL("DROP TABLE IF EXISTS pago")
        db.execSQL("DROP TABLE IF EXISTS deuda")
        db.execSQL("DROP TABLE IF EXISTS cliente")
        onCreate(db)
    }
}
