package com.mmq01.appdeudas.Principal

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.TelefonoUtils
import com.mmq01.appdeudas.databinding.DialogClienteBinding
import com.mmq01.appdeudas.databinding.DialogNombreBinding

object ClienteManualDialog {

    fun mostrar(
        activity: AppCompatActivity,
        nombreInicial: String? = null,
        celularInicial: String? = null,
        onGuardar: (nombre: String, celular: String) -> Boolean
    ) {
        val binding = DialogClienteBinding.inflate(activity.layoutInflater)
        binding.etNombre.setText(nombreInicial.orEmpty())
        binding.etCelular.setText(celularInicial.orEmpty())

        val dialogo = AlertDialog.Builder(activity)
            .setTitle(R.string.cliente_nuevo)
            .setView(binding.root)
            .setPositiveButton(R.string.accion_guardar, null)
            .setNegativeButton(R.string.accion_cancelar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
                val celular = binding.etCelular.text?.toString()?.trim().orEmpty()
                binding.tilNombre.error = null
                binding.tilCelular.error = null

                if (nombre.isEmpty()) {
                    binding.tilNombre.error = activity.getString(R.string.error_nombre)
                    return@setOnClickListener
                }
                if (!TelefonoUtils.esValido(TelefonoUtils.normalizar(celular))) {
                    binding.tilCelular.error = activity.getString(R.string.error_celular)
                    return@setOnClickListener
                }
                if (onGuardar(nombre, celular)) dialogo.dismiss()
            }
        }
        dialogo.show()
    }

    fun editarNombre(
        activity: AppCompatActivity,
        nombreActual: String,
        onGuardar: (nombre: String) -> Unit
    ) {
        val binding = DialogNombreBinding.inflate(activity.layoutInflater)
        binding.etNombre.setText(nombreActual)

        val dialogo = AlertDialog.Builder(activity)
            .setTitle(R.string.cliente_editar_nombre)
            .setView(binding.root)
            .setPositiveButton(R.string.accion_guardar, null)
            .setNegativeButton(R.string.accion_cancelar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
                if (nombre.isEmpty()) {
                    binding.tilNombre.error = activity.getString(R.string.error_nombre)
                    return@setOnClickListener
                }
                onGuardar(nombre)
                dialogo.dismiss()
            }
        }
        dialogo.show()
    }
}
