package com.mmq01.appdeudas.Clases

import java.io.File

/** Copia de la base guardada en el almacenamiento privado de la app (mismo celular). */
data class Respaldo(
    val archivo: File,
    val fecha: Long
) {
    val tamano: Long get() = archivo.length()
}
