package com.mmq01.appdeudas.Deudas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmq01.appdeudas.Adapter.AdapterAbono
import com.mmq01.appdeudas.Adapter.OnAbonoEliminar
import com.mmq01.appdeudas.Clases.Abono
import com.mmq01.appdeudas.DBHelper.AbonoDao
import com.mmq01.appdeudas.DBHelper.DeudaDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.databinding.SheetAbonosBinding

class AbonosBottomSheet : BottomSheetDialogFragment(), OnAbonoEliminar {

    companion object {
        const val RESULT = "abonos_result"
        private const val ARG_ID_DEUDA = "idDeuda"
        private const val ARG_SOLO_VER = "soloVer"

        /** Solo el formulario para registrar un abono. */
        fun abonar(idDeuda: Long): AbonosBottomSheet = crear(idDeuda, false)

        /** Solo el historial de abonos (con opción de eliminar). */
        fun verAbonos(idDeuda: Long): AbonosBottomSheet = crear(idDeuda, true)

        private fun crear(idDeuda: Long, soloVer: Boolean) =
            AbonosBottomSheet().apply {
                arguments = bundleOf(ARG_ID_DEUDA to idDeuda, ARG_SOLO_VER to soloVer)
            }
    }

    private var _binding: SheetAbonosBinding? = null
    private val binding get() = _binding!!

    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var deudaDao: DeudaDao
    private lateinit var abonoDao: AbonoDao
    private lateinit var adapter: AdapterAbono
    private var idDeuda: Long = 0
    private var soloVer = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAbonosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        helper = DeudasDatabaseHelper(requireContext())
        deudaDao = DeudaDao(helper)
        abonoDao = AbonoDao(helper)
        idDeuda = requireArguments().getLong(ARG_ID_DEUDA)
        soloVer = requireArguments().getBoolean(ARG_SOLO_VER, true)

        binding.tvTitulo.setText(if (soloVer) R.string.abonos_titulo else R.string.abonar_titulo)
        if (!soloVer) {
            binding.tvVacio.visibility = View.GONE
            binding.rvAbonos.visibility = View.GONE
        }

        adapter = AdapterAbono(emptyList(), true, this)
        binding.rvAbonos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAbonos.adapter = adapter

        binding.btnAgregar.setOnClickListener { agregar() }

        if (!cargar()) dismiss()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    /** Recarga header y lista. Devuelve false si la deuda ya no existe. */
    private fun cargar(): Boolean {
        val deuda = deudaDao.obtener(idDeuda) ?: return false
        val abonos = abonoDao.listar(idDeuda)
        val soloLectura = deuda.estado == "anulada"

        binding.tvNombreDeuda.text = deuda.nombre
        binding.tvValor.text = MonedaUtils.formatear(deuda.valor)
        binding.tvAbonado.text = MonedaUtils.formatear(deuda.totalAbonado)
        binding.tvSaldo.text = MonedaUtils.formatear(deuda.saldo)

        if (soloVer) {
            adapter.actualizarLista(abonos, soloLectura)
            val vacio = abonos.isEmpty()
            binding.tvVacio.visibility = if (vacio) View.VISIBLE else View.GONE
            binding.rvAbonos.visibility = if (vacio) View.GONE else View.VISIBLE
            binding.formAbono.visibility = View.GONE
        } else {
            if (soloLectura || deuda.saldo <= 0) return false
            binding.formAbono.visibility = View.VISIBLE
        }
        return true
    }

    private fun agregar() {
        val valor = MonedaUtils.parsear(binding.etValorAbono.text?.toString().orEmpty())
        val nota = binding.etNota.text?.toString()?.trim().orEmpty().ifBlank { null }
        binding.tilValorAbono.error = null

        if (valor == null || valor <= 0) {
            binding.tilValorAbono.error = getString(R.string.error_valor)
            return
        }
        val saldo = deudaDao.obtener(idDeuda)?.saldo ?: return
        if (valor > saldo) {
            binding.tilValorAbono.error = getString(R.string.abono_excede)
            return
        }

        try {
            abonoDao.insertar(idDeuda, valor, "abono", nota)
            notificarCambio()
            dismiss()
        } catch (e: ErrorBd) {
            binding.tilValorAbono.error = e.message
        }
    }

    override fun onAbonoEliminar(abono: Abono) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.abono_eliminar_titulo)
            .setMessage(R.string.abono_eliminar_mensaje)
            .setPositiveButton(R.string.accion_eliminar) { _, _ ->
                try {
                    abonoDao.anular(abono.id)
                    notificarCambio()
                    cargar()
                } catch (e: ErrorBd) {
                    binding.tilValorAbono.error = e.message
                }
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun notificarCambio() {
        parentFragmentManager.setFragmentResult(RESULT, bundleOf())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::helper.isInitialized) helper.close()
    }
}
