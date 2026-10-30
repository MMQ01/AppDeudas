package com.mmq01.appdeudas.Clases

data class Abono(
    val id: Long,
    val idDeuda: Long,
    val valor: Double,
    val tipo: String,
    val fecha: String,
    val nota: String?,
    val idPago: Long? = null,
    val estado: String = "activo"
)
