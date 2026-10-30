package com.mmq01.appdeudas.Deudas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmq01.appdeudas.DBHelper.DeudaDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.DBHelper.PagoDao
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.AvisoUtils
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.databinding.SheetDeudaFormBinding

class DeudaFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val RESULT = "deuda_form_result"
        private const val ARG_ID_CLIENTE = "idCliente"
        private const val ARG_ID_DEUDA = "idDeuda"

        fun newInstance(idCliente: String, idDeuda: Long? = null): DeudaFormBottomSheet {
            val args = bundleOf(ARG_ID_CLIENTE to idCliente)
            if (idDeuda != null) args.putLong(ARG_ID_DEUDA, idDeuda)
            return DeudaFormBottomSheet().apply { arguments = args }
        }
    }

    private var _binding: SheetDeudaFormBinding? = null
    private val binding get() = _binding!!

    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var deudaDao: DeudaDao
    private lateinit var pagoDao: PagoDao
    private lateinit var idCliente: String
    private var idDeuda: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetDeudaFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        helper = DeudasDatabaseHelper(requireContext())
        deudaDao = DeudaDao(helper)
        pagoDao = PagoDao(helper)
        idCliente = requireArguments().getString(ARG_ID_CLIENTE)!!
        idDeuda = if (requireArguments().containsKey(ARG_ID_DEUDA)) {
            requireArguments().getLong(ARG_ID_DEUDA)
        } else {
            null
        }

        idDeuda?.let { id ->
            val deuda = deudaDao.obtener(id)
            if (deuda == null) {
                dismiss()
                return
            }
            binding.tvTitulo.setText(R.string.editar_deuda)
            binding.etNombre.setText(deuda.nombre)
            binding.etDescripcion.setText(deuda.descripcion.orEmpty())
            binding.etValor.setText(deuda.valor.toLong().toString())
        }

        binding.btnGuardar.setOnClickListener { guardar() }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun guardar() {
        val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
        val descripcion = binding.etDescripcion.text?.toString()?.trim().orEmpty().ifBlank { null }
        val valor = MonedaUtils.parsear(binding.etValor.text?.toString().orEmpty())

        binding.tilNombre.error = null
        binding.tilValor.error = null

        if (nombre.isEmpty()) {
            binding.tilNombre.error = getString(R.string.error_nombre)
            return
        }
        if (valor == null || valor <= 0) {
            binding.tilValor.error = getString(R.string.error_valor)
            return
        }

        try {
            val id = idDeuda
            if (id == null) {
                val nueva = deudaDao.insertar(idCliente, nombre, descripcion, valor)
                val aFavor = pagoDao.saldoFavor(idCliente)
                if (aFavor > 0) {
                    binding.btnGuardar.isEnabled = false
                    ofrecerSaldoFavor(nueva, aFavor)
                    return
                }
            } else {
                deudaDao.actualizar(id, nombre, descripcion, valor)
            }
            terminar()
        } catch (e: ErrorBd) {
            binding.tilValor.error = e.message
        }
    }

    /** D6: el cliente tiene saldo a favor; se pregunta si se aplica a la deuda recién creada. */
    private fun ofrecerSaldoFavor(idNueva: Long, aFavor: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.saldo_favor_titulo)
            .setMessage(getString(R.string.saldo_favor_aplicar_mensaje, MonedaUtils.formatear(aFavor.toDouble())))
            .setPositiveButton(R.string.saldo_favor_aplicar) { _, _ ->
                try {
                    val aplicado = pagoDao.aplicarSaldoFavorADeuda(idCliente, idNueva)
                    AvisoUtils.mostrar(
                        requireActivity().findViewById(android.R.id.content),
                        getString(
                            R.string.saldo_favor_aplicado,
                            MonedaUtils.formatear(aplicado.toDouble())
                        ),
                        AvisoUtils.LARGO
                    )
                } catch (e: ErrorBd) {
                    AvisoUtils.mostrar(
                        requireActivity().findViewById(android.R.id.content),
                        e.message,
                        AvisoUtils.LARGO
                    )
                }
            }
            .setNegativeButton(R.string.saldo_favor_ahora_no, null)
            .setOnDismissListener { terminar() }
            .show()
    }

    private fun terminar() {
        parentFragmentManager.setFragmentResult(RESULT, bundleOf())
        dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        helper.close()
    }
}
