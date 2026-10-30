package com.mmq01.appdeudas.Utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Envía un texto por WhatsApp sin usar la red desde la app (solo lanza un Intent).
 *
 * Orden: chat directo del cliente → selector de contactos de WhatsApp → selector del sistema →
 * portapapeles.
 */
object WhatsAppUtils {

    private const val PAQUETE_WHATSAPP = "com.whatsapp"
    private const val PAQUETE_WHATSAPP_NEGOCIO = "com.whatsapp.w4b"

    /** Por encima de esto la URL del chat directo se vuelve frágil. */
    const val LIMITE_CHAT_DIRECTO = 1500

    /**
     * A → B → C. Devuelve `true` si se abrió alguna app; `false` solo si se copió al
     * portapapeles porque no había nada que manejar el Intent.
     */
    fun enviar(
        ctx: Context,
        telefono: String,
        texto: String,
        tituloChooser: String? = null
    ): Boolean {
        if (texto.length <= LIMITE_CHAT_DIRECTO && chatDirecto(ctx, telefono, texto)) return true
        if (selectorWhatsApp(ctx, texto)) return true
        if (selectorSistema(ctx, texto, tituloChooser)) return true
        copiar(ctx, texto)
        return false
    }

    /** Abre el chat del cliente con el texto listo (esquema `whatsapp://`). */
    fun chatDirecto(ctx: Context, telefono: String, texto: String): Boolean {
        val numero = telefono.filter(Char::isDigit)
        if (numero.isEmpty()) return false
        val uri = Uri.parse("whatsapp://send?phone=$numero&text=${Uri.encode(texto)}")
        return iniciar(ctx, Intent(Intent.ACTION_VIEW, uri))
    }

    /** WhatsApp (o WhatsApp Business) con su selector de contactos. */
    fun selectorWhatsApp(ctx: Context, texto: String): Boolean {
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        return iniciar(ctx, Intent(base).setPackage(PAQUETE_WHATSAPP)) ||
            iniciar(ctx, Intent(base).setPackage(PAQUETE_WHATSAPP_NEGOCIO))
    }

    /** Cualquier app que reciba texto (último recurso antes del portapapeles). */
    fun selectorSistema(ctx: Context, texto: String, titulo: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        return iniciar(ctx, Intent.createChooser(intent, titulo))
    }

    fun copiar(ctx: Context, texto: String) {
        val portapapeles = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        portapapeles.setPrimaryClip(ClipData.newPlainText("deudas", texto))
    }

    private fun iniciar(ctx: Context, intent: Intent): Boolean {
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
