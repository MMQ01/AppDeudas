package com.mmq01.appdeudas.Principal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmq01.appdeudas.Clases.Respaldo
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.AvisoUtils
import com.mmq01.appdeudas.Utils.RespaldoUtils
import com.mmq01.appdeudas.databinding.ItemRespaldoBinding
import com.mmq01.appdeudas.databinding.SheetRespaldosBinding

/**
 * Lista de respaldos guardados en este celular: crear uno nuevo o restaurar otro.
 * La restauración real la hace `ClientesActivity`, que es quien controla el helper de la base.
 */
class RespaldosBottomSheet : BottomSheetDialogFragment() {

    var onRestaurar: ((Respaldo) -> Unit)? = null

    private var _binding: SheetRespaldosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetRespaldosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCrear.setOnClickListener { crear() }
        binding.btnCancelar.setOnClickListener { dismiss() }
        cargar()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun cargar() {
        val lista = RespaldoUtils.listar(requireContext())
        binding.contenedor.removeAllViews()
        binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE

        for (respaldo in lista) {
            val fila = ItemRespaldoBinding.inflate(layoutInflater, binding.contenedor, false)
            fila.tvFecha.text = RespaldoUtils.textoFecha(respaldo)
            fila.tvTamano.text = RespaldoUtils.tamanoTexto(respaldo.tamano)
            fila.btnRestaurar.setOnClickListener { confirmarRestaurar(respaldo) }
            binding.contenedor.addView(fila.root)
        }
    }

    private fun crear() {
        try {
            RespaldoUtils.crear(requireContext())
            aviso(getString(R.string.respaldos_creado))
            cargar()
        } catch (e: ErrorBd) {
            aviso(e.message ?: getString(R.string.respaldos_error))
        } catch (e: Exception) {
            aviso(getString(R.string.respaldos_error))
        }
    }

    private fun confirmarRestaurar(respaldo: Respaldo) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.respaldos_restaurar_titulo)
            .setMessage(
                getString(
                    R.string.respaldos_restaurar_mensaje,
                    RespaldoUtils.textoFecha(respaldo)
                )
            )
            .setPositiveButton(R.string.respaldos_restaurar) { _, _ ->
                dismissNow()
                onRestaurar?.invoke(respaldo)
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun aviso(mensaje: String) {
        AvisoUtils.mostrar(
            requireActivity().findViewById(android.R.id.content),
            mensaje,
            AvisoUtils.LARGO
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
