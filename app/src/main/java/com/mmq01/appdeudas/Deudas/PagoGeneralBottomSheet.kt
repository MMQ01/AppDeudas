package com.mmq01.appdeudas.Deudas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmq01.appdeudas.Adapter.AdapterReparto
import com.mmq01.appdeudas.DBHelper.ClienteDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.DBHelper.PagoDao
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.AvisoUtils
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.Utils.RepartoUtils
import com.mmq01.appdeudas.databinding.SheetPagoGeneralBinding

/**
 * Abono general: el cliente entrega un valor sin decir a qué deuda va. Se reparte entre sus
 * deudas pendientes (más antigua primero), el usuario puede ajustar cada monto, y lo que no se
 * asigne queda como saldo a favor.
 */
class PagoGeneralBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val RESULT = "pago_general_result"
        private const val ARG_ID_CLIENTE = "idCliente"

        fun newInstance(idCliente: String) =
            PagoGeneralBottomSheet().apply { arguments = bundleOf(ARG_ID_CLIENTE to idCliente) }
    }

    private var _binding: SheetPagoGeneralBinding? = null
    private val binding get() = _binding!!

    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var pagoDao: PagoDao
    private lateinit var adapter: AdapterReparto
    private lateinit var idCliente: String

    private var saldoFavor = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetPagoGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        helper = DeudasDatabaseHelper(requireContext())
        pagoDao = PagoDao(helper)
        idCliente = requireArguments().getString(ARG_ID_CLIENTE)!!

        val cliente = ClienteDao(helper).obtener(idCliente)
        if (cliente == null) {
            dismiss()
            return
        }
        binding.tvCliente.text = cliente.nombre
        binding.tvPendiente.text = MonedaUtils.formatear(pagoDao.totalPendiente(idCliente).toDouble())

        saldoFavor = pagoDao.saldoFavor(idCliente)
        if (saldoFavor > 0) {
            val texto = MonedaUtils.formatear(saldoFavor.toDouble())
            binding.bloqueFavor.visibility = View.VISIBLE
            binding.tvFavor.text = texto
            binding.chkUsarFavor.visibility = View.VISIBLE
            binding.chkUsarFavor.text = getString(R.string.pg_usar_favor, texto)
        }

        adapter = AdapterReparto { actualizarResumen() }
        binding.rvReparto.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReparto.adapter = adapter

        binding.etValor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.tilValor.error = null
                recalcular()
            }
        })
        binding.chkUsarFavor.setOnCheckedChangeListener { _, _ -> recalcular() }
        binding.btnCancelar.setOnClickListener { dismiss() }
        binding.btnRegistrar.setOnClickListener { registrar() }

        recalcular()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun valorNuevo(): Long =
        binding.etValor.text?.toString()?.filter(Char::isDigit)?.toLongOrNull() ?: 0L

    private fun usaFavor(): Boolean = saldoFavor > 0 && binding.chkUsarFavor.isChecked

    /** Dinero repartible: lo que entrega ahora más, si se marca, el saldo a favor existente. */
    private fun disponible(): Long = valorNuevo() + if (usaFavor()) saldoFavor else 0L

    /** Vuelve a proponer el reparto (más antigua primero) con el valor actual. */
    private fun recalcular() {
        val reparto = pagoDao.previsualizar(idCliente, disponible())
        adapter.actualizarLineas(reparto.lineas)
        val sinDeudas = reparto.lineas.isEmpty()
        binding.tvSinDeudas.visibility = if (sinDeudas) View.VISIBLE else View.GONE
        binding.rvReparto.visibility = if (sinDeudas) View.GONE else View.VISIBLE
        actualizarResumen()
    }

    private fun actualizarResumen() {
        val disponible = disponible()
        val lineas = adapter.actuales
        val asignado = lineas.sumOf { it.monto }
        val error = if (disponible > 0) {
            RepartoUtils.validar(lineas.filter { it.monto > 0 }, disponible)
        } else {
            null
        }

        binding.tvResumen.text = getString(
            R.string.pg_resumen,
            MonedaUtils.formatear(asignado.toDouble()),
            MonedaUtils.formatear((disponible - asignado).coerceAtLeast(0).toDouble())
        )
        binding.tvError.visibility = if (error == null) View.GONE else View.VISIBLE
        binding.tvError.text = error
        binding.btnRegistrar.isEnabled = error == null && (valorNuevo() > 0 || asignado > 0)
    }

    private fun registrar() {
        val valor = valorNuevo()
        val nota = binding.etNota.text?.toString()?.trim().orEmpty().ifBlank { null }
        val lineas = adapter.actuales.filter { it.monto > 0 }
        val deudasConAbono = lineas.size
        val asignado = lineas.sumOf { it.monto }
        val aFavorDespues = disponible() - asignado

        try {
            pagoDao.registrar(idCliente, valor, lineas, nota, usaFavor())
        } catch (e: ErrorBd) {
            binding.tilValor.error = e.message
            return
        }

        val mensaje = if (deudasConAbono == 0) {
            getString(R.string.pg_registrado_favor, MonedaUtils.formatear(valor.toDouble()))
        } else {
            getString(
                R.string.pg_registrado,
                MonedaUtils.formatear(asignado.toDouble()),
                deudasConAbono
            ) + if (aFavorDespues > 0) {
                " " + getString(R.string.pg_registrado_sobra, MonedaUtils.formatear(aFavorDespues.toDouble()))
            } else {
                ""
            }
        }
        AvisoUtils.mostrar(
            requireActivity().findViewById(android.R.id.content),
            mensaje,
            AvisoUtils.LARGO
        )
        parentFragmentManager.setFragmentResult(RESULT, bundleOf())
        dismiss()
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
