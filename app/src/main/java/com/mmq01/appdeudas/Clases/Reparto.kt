package com.mmq01.appdeudas.Clases

/** Deuda pendiente candidata a recibir parte de un pago general. Montos en pesos enteros. */
data class DeudaPendiente(
    val id: Long,
    val nombre: String,
    val fechaCreacion: String,
    val saldo: Long
)

/** Parte del pago que se aplicará a una deuda. `monto` es editable por el usuario. */
data class LineaReparto(
    val idDeuda: Long,
    val nombre: String,
    val fechaCreacion: String,
    val saldo: Long,
    val monto: Long
) {
    val quedaPagada: Boolean get() = monto == saldo
}

/** Pago general con saldo a favor disponible. Montos en pesos enteros. */
data class FuenteFavor(
    val idPago: Long,
    val valorPago: Long,
    val fecha: String,
    val disponible: Long
)

/** Parte del saldo a favor de `fuente` que se aplicará a una deuda. */
data class AplicacionFavor(
    val fuente: FuenteFavor,
    val idDeuda: Long,
    val monto: Long
)

/** `saldoFavor` > 0: el valor supera lo asignado y la diferencia queda a favor del cliente. */
data class Reparto(
    val lineas: List<LineaReparto>,
    val saldoFavor: Long
) {
    val totalAsignado: Long get() = lineas.sumOf { it.monto }
}
