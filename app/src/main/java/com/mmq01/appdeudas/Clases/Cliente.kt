package com.mmq01.appdeudas.Clases

data class Cliente(
    val idCliente: String,
    val nombre: String,
    val telefonoOriginal: String? = null,
    val contactoUri: String? = null,
    val origen: String = "manual",
    val estado: String = "activo",
    val fechaCreacion: String = "",
    val fechaModificacion: String = ""
)

data class ResumenCliente(
    val idCliente: String,
    val nombre: String,
    val telefonoOriginal: String?,
    val contactoUri: String?,
    val origen: String,
    val estado: String,
    val totalDeudas: Int,
    val deudasPendientes: Int,
    val valorTotal: Double,
    val saldoPendiente: Double,
    val totalPagado: Double,
    val saldoFavor: Double = 0.0
)
