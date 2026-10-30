package com.mmq01.appdeudas

import com.mmq01.appdeudas.Utils.MonedaUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class MonedaUtilsTest {

    @Test
    fun formatea_pesos_sin_decimales() {
        assertEquals("$ 1.250.000", MonedaUtils.formatear(1_250_000.0))
        assertEquals("$ 0", MonedaUtils.formatear(0.0))
    }

    @Test
    fun parsea_texto_con_separadores() {
        assertEquals(1_250_000.0, MonedaUtils.parsear("1.250.000")!!, 0.01)
        assertEquals(500_000.0, MonedaUtils.parsear("500000")!!, 0.01)
    }
}
