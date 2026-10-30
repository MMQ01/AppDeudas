package com.mmq01.appdeudas.Deudas

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mmq01.appdeudas.Adapter.AdapterDeuda
import com.mmq01.appdeudas.Adapter.OnDeudaAccion
import com.mmq01.appdeudas.Clases.DeudaDetalle
import com.mmq01.appdeudas.DBHelper.ClienteDao
import com.mmq01.appdeudas.DBHelper.DeudaDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.DBHelper.PagoDao
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.AvisoUtils
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.Utils.TelefonoUtils
import com.mmq01.appdeudas.databinding.ActivityDeudasBinding

class DeudasActivity : AppCompatActivity(), OnDeudaAccion {

    companion object {
        const val EXTRA_ID_CLIENTE = "idCliente"
    }

    private lateinit var binding: ActivityDeudasBinding
    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var clienteDao: ClienteDao
    private lateinit var deudaDao: DeudaDao
    private lateinit var pagoDao: PagoDao
    private lateinit var adapter: AdapterDeuda

    private lateinit var idCliente: String
    private var filtroEstado: String? = "pendiente"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeudasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idCliente = intent.getStringExtra(EXTRA_ID_CLIENTE).orEmpty()
        if (idCliente.isEmpty()) {
            finish()
            return
        }

        helper = DeudasDatabaseHelper(this)
        clienteDao = ClienteDao(helper)
        deudaDao = DeudaDao(helper)
        pagoDao = PagoDao(helper)

        val cliente = clienteDao.obtener(idCliente)
        if (cliente == null) {
            finish()
            return
        }
        binding.toolbar.title = cliente.nombre
        binding.toolbar.subtitle = TelefonoUtils.paraMostrar(
            cliente.telefonoOriginal ?: cliente.idCliente
        )
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = AdapterDeuda(emptyList(), this)
        binding.rvDeudas.layoutManager = LinearLayoutManager(this)
        binding.rvDeudas.adapter = adapter

        binding.chips.setOnCheckedStateChangeListener { _, checkedIds ->
            filtroEstado = when (checkedIds.firstOrNull()) {
                R.id.chipPagadas -> "pagada"
                R.id.chipAnuladas -> "anulada"
                R.id.chipTodas -> null
                else -> "pendiente"
            }
            cargar()
        }

        supportFragmentManager.setFragmentResultListener(
            DeudaFormBottomSheet.RESULT,
            this
        ) { _, _ -> cargar() }

        supportFragmentManager.setFragmentResultListener(
            AbonosBottomSheet.RESULT,
            this
        ) { _, _ -> cargar() }

        supportFragmentManager.setFragmentResultListener(
            PagoGeneralBottomSheet.RESULT,
            this
        ) { _, _ -> cargar() }

        binding.fab.setOnClickListener { mostrarFormulario(null) }
        binding.btnAbonoGeneral.setOnClickListener {
            PagoGeneralBottomSheet.newInstance(idCliente)
                .show(supportFragmentManager, "pago_general")
        }

        cargar()
    }

    override fun onResume() {
        super.onResume()
        if (::deudaDao.isInitialized) cargar()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::helper.isInitialized) helper.close()
    }

    override fun onDeudaClick(deuda: DeudaDetalle) {
        abrirVerAbonos(deuda)
    }

    override fun onDeudaMenu(deuda: DeudaDetalle, ancla: View) {
        val popup = PopupMenu(this, ancla)
        when (deuda.estado) {
            "pendiente" -> {
                popup.menu.add(0, 1, 0, R.string.accion_abonar)
                popup.menu.add(0, 5, 1, R.string.accion_ver_abonos)
                popup.menu.add(0, 2, 2, R.string.accion_marcar_pagada)
                popup.menu.add(0, 3, 3, R.string.accion_editar)
                popup.menu.add(0, 4, 4, R.string.accion_anular)
            }

            "pagada" -> {
                popup.menu.add(0, 5, 0, R.string.accion_ver_abonos)
                popup.menu.add(0, 6, 1, R.string.accion_reabrir)
                popup.menu.add(0, 3, 2, R.string.accion_editar)
                popup.menu.add(0, 4, 3, R.string.accion_anular)
            }

            else -> popup.menu.add(0, 5, 0, R.string.accion_ver_abonos)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> abrirAbonar(deuda)
                5 -> abrirVerAbonos(deuda)
                2 -> confirmarPagar(deuda)
                3 -> mostrarFormulario(deuda.id)
                4 -> confirmarAnular(deuda)
                6 -> confirmarReabrir(deuda)
            }
            true
        }
        popup.show()
    }

    private fun cargar() {
        val deudas = deudaDao.listar(idCliente, filtroEstado)
        adapter.actualizarLista(deudas)
        binding.tvTotal.text = MonedaUtils.formatear(deudaDao.totalPendiente(idCliente))

        val aFavor = pagoDao.saldoFavor(idCliente)
        binding.tvFavor.visibility = if (aFavor > 0) View.VISIBLE else View.GONE
        binding.tvFavor.text = getString(R.string.a_favor_etiqueta, MonedaUtils.formatear(aFavor.toDouble()))

        val vacio = deudas.isEmpty()
        binding.tvVacio.visibility = if (vacio) View.VISIBLE else View.GONE
        binding.rvDeudas.visibility = if (vacio) View.GONE else View.VISIBLE
    }

    private fun mostrarFormulario(idDeuda: Long?) {
        DeudaFormBottomSheet.newInstance(idCliente, idDeuda)
            .show(supportFragmentManager, "deuda_form")
    }

    private fun abrirAbonar(deuda: DeudaDetalle) {
        AbonosBottomSheet.abonar(deuda.id)
            .show(supportFragmentManager, "abonar")
    }

    private fun abrirVerAbonos(deuda: DeudaDetalle) {
        AbonosBottomSheet.verAbonos(deuda.id)
            .show(supportFragmentManager, "abonos")
    }

    private fun confirmarPagar(deuda: DeudaDetalle) {
        confirmar(
            R.string.confirmar_pagar_titulo,
            R.string.confirmar_pagar_mensaje,
            R.string.accion_marcar_pagada
        ) {
            try {
                deudaDao.marcarPagada(deuda.id)
                aviso(getString(R.string.deuda_pagada))
                cargar()
            } catch (e: ErrorBd) {
                snack(e.message)
            }
        }
    }

    private fun confirmarReabrir(deuda: DeudaDetalle) {
        confirmar(
            R.string.confirmar_reabrir_titulo,
            R.string.confirmar_reabrir_mensaje,
            R.string.accion_reabrir
        ) {
            deudaDao.reabrir(deuda.id)
            aviso(getString(R.string.deuda_reabierta))
            cargar()
        }
    }

    private fun confirmarAnular(deuda: DeudaDetalle) {
        confirmar(
            R.string.confirmar_anular_titulo,
            R.string.confirmar_anular_mensaje,
            R.string.accion_anular
        ) {
            deudaDao.anular(deuda.id)
            aviso(getString(R.string.deuda_anulada))
            cargar()
        }
    }

    private fun confirmar(
        titulo: Int,
        mensaje: Int,
        textoPositivo: Int,
        onConfirmar: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoPositivo) { _, _ -> onConfirmar() }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun snack(mensaje: String?) {
        Snackbar.make(
            binding.root,
            mensaje ?: getString(R.string.error_operacion),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun aviso(mensaje: String) {
        AvisoUtils.mostrar(binding.root, mensaje, AvisoUtils.LARGO)
    }
}
