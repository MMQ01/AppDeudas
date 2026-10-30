package com.mmq01.appdeudas

import com.mmq01.appdeudas.Utils.TelefonoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelefonoUtilsTest {

    @Test
    fun normaliza_celular_de_diez_digitos() {
        assertEquals("573001234567", TelefonoUtils.normalizar("3001234567"))
    }

    @Test
    fun normaliza_con_espacios_guiones_y_mas() {
        assertEquals("573001234567", TelefonoUtils.normalizar("+57 300-123-45-67"))
        assertEquals("573001234567", TelefonoUtils.normalizar("(300) 123 4567"))
    }

    @Test
    fun deja_intacto_el_numero_ya_normalizado() {
        assertEquals("573001234567", TelefonoUtils.normalizar("573001234567"))
    }

    @Test
    fun valida_solo_celulares_colombianos() {
        assertTrue(TelefonoUtils.esValido("573001234567"))
        assertFalse(TelefonoUtils.esValido("3001234567"))
        assertFalse(TelefonoUtils.esValido("571234567890"))
        assertFalse(TelefonoUtils.esValido("57300123456"))
    }

    @Test
    fun formatea_para_mostrar() {
        assertEquals("300 123 4567", TelefonoUtils.paraMostrar("573001234567"))
    }
}
