package com.mmq01.appdeudas.Utils

import com.mmq01.appdeudas.Clases.AplicacionFavor
import com.mmq01.appdeudas.Clases.DeudaPendiente
import com.mmq01.appdeudas.Clases.FuenteFavor
import com.mmq01.appdeudas.Clases.LineaReparto
import com.mmq01.appdeudas.Clases.Reparto

/** Reparto de un pago general entre deudas. Lógica pura en enteros (COP sin decimales). */
object RepartoUtils {

    const val PREFIJO_ABONO_GENERAL = "Abono general de"

    /** Más antigua primero. Las deudas con saldo <= 0 se ignoran. Lo que sobra queda a favor. */
    fun fifo(deudas: List<DeudaPendiente>, valor: Long): Reparto {
        var restante = valor.coerceAtLeast(0)
        val lineas = deudas
            .filter { it.saldo > 0 }
            .sortedWith(compareBy({ it.fechaCreacion }, { it.id }))
            .map { d ->
                val monto = minOf(restante, d.saldo)
                restante -= monto
                LineaReparto(d.id, d.nombre, d.fechaCreacion, d.saldo, monto)
            }
        return Reparto(lineas, restante)
    }

    /**
     * Valida un reparto (posiblemente editado a mano) contra el valor del pago.
     * Asignar menos que el valor es válido: la diferencia queda a favor del cliente.
     * Devuelve el mensaje de error, o null si es válido.
     */
    fun validar(lineas: List<LineaReparto>, valor: Long): String? {
        if (valor <= 0) return "El valor debe ser mayor a cero"
        if (lineas.any { it.monto < 0 }) return "Los montos no pueden ser negativos"
        if (lineas.any { it.monto > it.saldo }) return "Un monto supera el saldo de su deuda"
        if (lineas.sumOf { it.monto } > valor) return "Se asignó más que el valor del pago"
        return null
    }

    /**
     * Reparte las líneas entre las fuentes de saldo a favor, del pago más antiguo al más
     * nuevo. Si el saldo a favor no alcanza, cada línea recibe solo lo que haya.
     */
    fun repartirFavor(fuentes: List<FuenteFavor>, lineas: List<LineaReparto>): List<AplicacionFavor> {
        val disponibles = fuentes
            .filter { it.disponible > 0 }
            .sortedWith(compareBy({ it.fecha }, { it.idPago }))
            .map { it to it.disponible }
            .toMutableList()
        val resultado = mutableListOf<AplicacionFavor>()
        for (linea in lineas) {
            var falta = minOf(linea.monto, linea.saldo)
            while (falta > 0 && disponibles.isNotEmpty()) {
                val (fuente, queda) = disponibles.first()
                val monto = minOf(falta, queda)
                resultado.add(AplicacionFavor(fuente, linea.idDeuda, monto))
                falta -= monto
                if (queda == monto) disponibles.removeAt(0) else disponibles[0] = fuente to queda - monto
            }
        }
        return resultado
    }

    /**
     * Nota de cada abono que sale de un pago general, para saber de cuánto fue el pago:
     * "Abono general de $ 20.000 · aplicado aquí $ 300 · repartido en 3 deudas · a favor $ 4.700".
     */
    fun notaAbonoGeneral(
        valorPago: Long,
        aplicadoAqui: Long,
        numDeudas: Int,
        saldoFavor: Long,
        notaUsuario: String?
    ): String {
        val partes = mutableListOf(
            "$PREFIJO_ABONO_GENERAL ${pesos(valorPago)}",
            "aplicado aquí ${pesos(aplicadoAqui)}"
        )
        if (numDeudas > 1) partes.add("repartido en $numDeudas deudas")
        if (saldoFavor > 0) partes.add("a favor ${pesos(saldoFavor)}")
        if (!notaUsuario.isNullOrBlank()) partes.add(notaUsuario.trim())
        return partes.joinToString(" · ")
    }

    /** Nota del abono que usa saldo a favor: "Saldo a favor del abono general de $ 20.000 (28/10/2026)". */
    fun notaSaldoFavor(valorPago: Long, fechaPago: String): String =
        "Saldo a favor del abono general de ${pesos(valorPago)} (${fechaCorta(fechaPago)})"

    /** "2026-10-28 15:04:00" → "28/10/2026". */
    fun fechaCorta(fecha: String): String = FechaUtils.diaLocal(fecha)

    private fun pesos(valor: Long): String = MonedaUtils.formatear(valor.toDouble())
}
