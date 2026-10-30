package com.mmq01.appdeudas.Utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Fechas de la base (guardadas en UTC) mostradas en la zona horaria del equipo. */
object FechaUtils {

    private const val ZONA_BD = "UTC"
    private const val FORMATO_BD = "yyyy-MM-dd HH:mm:ss"
    private const val FORMATO_DIA = "dd/MM/yyyy"

    /** `yyyy-MM-dd HH:mm:ss` (UTC) → `dd/MM/yyyy` local. Acepta también solo `yyyy-MM-dd`. */
    fun diaLocal(fecha: String?): String {
        if (fecha.isNullOrBlank()) return ""
        val texto = fecha.trim()
        if (texto.length < 19) return corta(texto)
        return try {
            val entrada = SimpleDateFormat(FORMATO_BD, Locale.US).apply {
                timeZone = TimeZone.getTimeZone(ZONA_BD)
            }
            val instante = entrada.parse(texto.substring(0, 19)) ?: return corta(texto)
            SimpleDateFormat(FORMATO_DIA, Locale("es", "CO")).format(instante)
        } catch (e: ParseException) {
            corta(texto)
        }
    }

    /** Solo reordena `yyyy-MM-dd` → `dd/MM/yyyy`, sin conversión de zona. */
    fun corta(fecha: String?): String {
        if (fecha.isNullOrBlank()) return ""
        val dia = fecha.trim().take(10)
        val partes = dia.split("-")
        return if (partes.size == 3) partes.reversed().joinToString("/") else dia
    }
}
