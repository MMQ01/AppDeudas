package com.mmq01.appdeudas.Clases

data class Deuda(
    val id: Long,
    val idCliente: String,
    val nombre: String,
    val descripcion: String?,
    val valor: Double,
    val saldo: Double,
    val estado: String,
    val fechaCreacion: String,
    val fechaPago: String?,
    val fechaAnulacion: String?,
    val fechaModificacion: String
)

data class DeudaDetalle(
    val id: Long,
    val idCliente: String,
    val nombre: String,
    val descripcion: String?,
    val valor: Double,
    val saldo: Double,
    val estado: String,
    val fechaCreacion: String,
    val fechaPago: String?,
    val fechaAnulacion: String?,
    val fechaModificacion: String,
    val totalAbonado: Double,
    val numAbonos: Int
)
