package com.mmq01.appdeudas.Utils

object TelefonoUtils {

    fun normalizar(celular: String): String {
        val digitos = celular.filter(Char::isDigit)
        return when {
            digitos.length == 10 && digitos.startsWith("3") -> "57$digitos"
            digitos.length == 12 && digitos.startsWith("573") -> digitos
            else -> digitos
        }
    }

    fun esValido(celularNormalizado: String): Boolean =
        celularNormalizado.length == 12 &&
            celularNormalizado.startsWith("57") &&
            celularNormalizado[2] == '3'

    fun paraMostrar(celular: String?): String {
        if (celular.isNullOrBlank()) return ""
        val digitos = celular.filter(Char::isDigit)
        return if (digitos.length == 12 && digitos.startsWith("57")) {
            "${digitos.substring(2, 5)} ${digitos.substring(5, 8)} ${digitos.substring(8)}"
        } else {
            celular
        }
    }
}
