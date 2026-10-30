package com.mmq01.appdeudas.Utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MonedaUtils {

    fun formatear(valor: Double): String = "$ " + sinSimbolo(valor)

    fun sinSimbolo(valor: Double): String {
        val formato = DecimalFormat("#,##0", DecimalFormatSymbols(Locale("es", "CO")))
        return formato.format(valor)
    }

    fun parsear(texto: String): Double? {
        val limpio = texto.trim().replace(".", "").replace(",", ".")
        return limpio.toDoubleOrNull()
    }
}
