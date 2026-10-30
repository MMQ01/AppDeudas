package com.mmq01.appdeudas.Utils

import com.mmq01.appdeudas.Clases.DeudaDetalle

/**
 * Arma el resumen de deudas pendientes que se comparte por WhatsApp.
 *
 * Formato (sin saludo, sin introducción y sin saldo a favor):
 *
 * ```
 * • Mercado (12/09/2026) — $ 150.000
 *   Cuota de la moto
 * • Préstamo (20/09/2026) — $ 300.000
 *
 * *Total pendiente:* $ 450.000
 * ```
 */
object ResumenUtils {

    const val ETIQUETA_TOTAL = "Total pendiente:"

    /** `deudas` debería venir ya filtrada a pendientes; igual se ignoran saldos <= 0. */
    fun construir(deudas: List<DeudaDetalle>): String {
        val ordenadas = deudas
            .filter { it.saldo > 0 }
            .sortedWith(compareBy({ it.fechaCreacion }, { it.id }))

        val texto = StringBuilder()
        var total = 0.0
        for ((indice, deuda) in ordenadas.withIndex()) {
            if (indice > 0) texto.append('\n')
            texto.append("• ").append(deuda.nombre)
                .append(" (").append(fechaCorta(deuda.fechaCreacion)).append(") — ")
                .append(MonedaUtils.formatear(deuda.saldo))
            val descripcion = deuda.descripcion?.trim()
            if (!descripcion.isNullOrEmpty()) {
                texto.append('\n').append("  ").append(descripcion)
            }
            total += deuda.saldo
        }
        if (texto.isNotEmpty()) texto.append("\n\n")
        texto.append("*").append(ETIQUETA_TOTAL).append("* ")
            .append(MonedaUtils.formatear(total))
        return texto.toString()
    }

    /** `yyyy-MM-dd HH:mm:ss` (UTC) → `dd/MM/yyyy` local. Si no tiene ese formato, lo deja igual. */
    fun fechaCorta(fecha: String): String = FechaUtils.diaLocal(fecha)
}
