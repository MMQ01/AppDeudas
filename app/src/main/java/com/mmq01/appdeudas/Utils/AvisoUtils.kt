package com.mmq01.appdeudas.Utils

import android.view.View
import com.google.android.material.snackbar.Snackbar

/**
 * Avisos dentro de la app. Se usa Snackbar en vez de Toast: vive dentro de la jerarquía de
 * vistas (se puede probar con uiautomator) y respeta el tema claro/oscuro.
 */
object AvisoUtils {

    const val CORTO = Snackbar.LENGTH_SHORT
    const val LARGO = Snackbar.LENGTH_LONG

    fun mostrar(ancla: View?, mensaje: CharSequence?, duracion: Int = CORTO) {
        val texto = mensaje?.toString()?.trim().orEmpty()
        if (ancla == null || texto.isEmpty()) return
        Snackbar.make(ancla, texto, duracion).show()
    }
}
