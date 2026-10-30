package com.mmq01.appdeudas.Utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class Contacto(val nombre: String, val numeros: List<String>)

object ContactosHelper {

    fun leerContacto(context: Context, uri: Uri): Contacto? {
        val proyeccion = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        context.contentResolver.query(uri, proyeccion, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            val nombre = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            ) ?: ""
            val numeros = mutableListOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(id),
                null
            )?.use { phones ->
                while (phones.moveToNext()) {
                    val numero = phones.getString(0)
                    if (!numero.isNullOrBlank() && !numeros.contains(numero)) numeros.add(numero)
                }
            }
            return Contacto(nombre, numeros)
        }
        return null
    }

    fun buscarNombre(context: Context, numero: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(numero)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}
