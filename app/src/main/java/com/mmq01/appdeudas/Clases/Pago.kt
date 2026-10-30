package com.mmq01.appdeudas.Clases

/**
 * Abono general del cliente. `valor` es lo que entregó; `aplicado`, `saldoFavor` y
 * `numDeudas` salen de vw_pago (derivados de sus abonos activos).
 */
data class Pago(
    val id: Long,
    val idCliente: String,
    val valor: Double,
    val fecha: String,
    val nota: String?,
    val aplicado: Double,
    val saldoFavor: Double,
    val numDeudas: Int,
    val estado: String = "activo"
)
