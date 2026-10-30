package com.mmq01.appdeudas.Principal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mmq01.appdeudas.Adapter.AdapterCliente
import com.mmq01.appdeudas.Adapter.OnClienteClick
import com.mmq01.appdeudas.Adapter.OnClienteLongClick
import com.mmq01.appdeudas.Clases.Cliente
import com.mmq01.appdeudas.Clases.ResumenCliente
import com.mmq01.appdeudas.Clases.Respaldo
import com.mmq01.appdeudas.DBHelper.ClienteDao
import com.mmq01.appdeudas.DBHelper.DeudaDao
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import com.mmq01.appdeudas.Deudas.DeudasActivity
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.AvisoUtils
import com.mmq01.appdeudas.Utils.Contacto
import com.mmq01.appdeudas.Utils.ContactosHelper
import com.mmq01.appdeudas.Utils.RespaldoScheduler
import com.mmq01.appdeudas.Utils.RespaldoUtils
import com.mmq01.appdeudas.Utils.ResumenUtils
import com.mmq01.appdeudas.Utils.TelefonoUtils
import com.mmq01.appdeudas.Utils.WhatsAppUtils
import com.mmq01.appdeudas.databinding.ActivityClientesBinding
import java.util.concurrent.Executors

class ClientesActivity : AppCompatActivity(), OnClienteClick, OnClienteLongClick {

    companion object {
        /** Aviso que debe salir después de recrear la pantalla tras restaurar un respaldo. */
        private var avisoPendiente: String? = null
    }

    private lateinit var binding: ActivityClientesBinding
    private lateinit var helper: DeudasDatabaseHelper
    private lateinit var clienteDao: ClienteDao
    private lateinit var deudaDao: DeudaDao
    private lateinit var adapter: AdapterCliente
    private val ejecutor = Executors.newSingleThreadExecutor()
    private var filtro: String = ""

    private val permisoContactos =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                selectorContacto.launch(null)
            } else {
                Snackbar.make(binding.root, R.string.permiso_contactos, Snackbar.LENGTH_LONG).show()
            }
        }

    private val selectorContacto =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            if (uri != null) procesarContacto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        helper = DeudasDatabaseHelper(this)
        clienteDao = ClienteDao(helper)
        deudaDao = DeudaDao(helper)

        adapter = AdapterCliente(emptyList(), this, this)
        binding.rvClientes.layoutManager = LinearLayoutManager(this)
        binding.rvClientes.adapter = adapter

        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filtro = query.orEmpty()
                adapter.filtrar(filtro)
                actualizarVacio()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filtro = newText.orEmpty()
                adapter.filtrar(filtro)
                actualizarVacio()
                return true
            }
        })

        binding.fab.setOnClickListener {
            val menu = MenuClienteBottomSheet()
            menu.onDesdeContactos = { pedirContacto() }
            menu.onManual = { mostrarAltaManual() }
            menu.show(supportFragmentManager, "menu_cliente")
        }

        binding.toolbar.inflateMenu(R.menu.menu_clientes)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.accion_respaldos) {
                abrirRespaldos()
                true
            } else {
                false
            }
        }

        avisoPendiente?.let {
            aviso(it)
            avisoPendiente = null
        }

        programarRespaldo()
    }

    override fun onResume() {
        super.onResume()
        cargarClientes()
        refrescarNombresDesdeContactos()
    }

    override fun onDestroy() {
        super.onDestroy()
        ejecutor.shutdown()
        helper.close()
    }

    override fun onClienteClick(cliente: ResumenCliente) {
        abrirDeudas(cliente.idCliente)
    }

    override fun onClienteLongClick(cliente: ResumenCliente, ancla: View) {
        val popup = PopupMenu(this, ancla)
        popup.menu.add(0, 3, 0, R.string.compartir_menu)
        popup.menu.add(0, 1, 1, R.string.cliente_editar_nombre)
        popup.menu.add(0, 2, 2, R.string.accion_eliminar)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                3 -> compartirDeudas(cliente)
                1 -> editarNombre(cliente)
                2 -> confirmarEliminar(cliente)
            }
            true
        }
        popup.show()
    }

    private fun cargarClientes() {
        val lista = clienteDao.listarActivos()
        adapter.actualizarLista(lista, filtro)
        actualizarVacio()
    }

    private fun actualizarVacio() {
        val vacio = adapter.itemCount == 0
        binding.tvVacio.visibility = if (vacio) View.VISIBLE else View.GONE
        binding.rvClientes.visibility = if (vacio) View.GONE else View.VISIBLE
    }

    private fun abrirDeudas(idCliente: String) {
        startActivity(
            Intent(this, DeudasActivity::class.java)
                .putExtra(DeudasActivity.EXTRA_ID_CLIENTE, idCliente)
        )
    }

    private fun pedirContacto() {
        val estado = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
        if (estado == PackageManager.PERMISSION_GRANTED) {
            selectorContacto.launch(null)
        } else {
            permisoContactos.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun procesarContacto(uri: Uri) {
        ejecutor.execute {
            val contacto = ContactosHelper.leerContacto(this, uri)
            runOnUiThread {
                if (contacto == null) {
                    aviso(getString(R.string.contacto_error))
                    return@runOnUiThread
                }
                when {
                    contacto.numeros.isEmpty() -> {
                        aviso(getString(R.string.contacto_sin_numero))
                        mostrarAltaManual(nombreInicial = contacto.nombre)
                    }

                    contacto.numeros.size == 1 ->
                        guardarDesdeContacto(contacto.nombre, contacto.numeros.first(), uri)

                    else -> elegirNumero(contacto, uri)
                }
            }
        }
    }

    private fun elegirNumero(contacto: Contacto, uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.seleccionar_numero)
            .setItems(contacto.numeros.toTypedArray()) { _, which ->
                guardarDesdeContacto(contacto.nombre, contacto.numeros[which], uri)
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun guardarDesdeContacto(nombre: String, numero: String, uri: Uri) {
        val normalizado = TelefonoUtils.normalizar(numero)
        if (!TelefonoUtils.esValido(normalizado)) {
            aviso(getString(R.string.error_celular))
            mostrarAltaManual(nombreInicial = nombre)
            return
        }
        try {
            registrarCliente(
                Cliente(
                    idCliente = normalizado,
                    nombre = nombre.ifBlank { TelefonoUtils.paraMostrar(normalizado) },
                    telefonoOriginal = numero,
                    contactoUri = uri.toString(),
                    origen = "contacto"
                )
            )
        } catch (e: ErrorBd) {
            Snackbar.make(
                binding.root,
                e.message ?: getString(R.string.error_operacion),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun mostrarAltaManual(
        nombreInicial: String? = null,
        celularInicial: String? = null
    ) {
        ClienteManualDialog.mostrar(this, nombreInicial, celularInicial) { nombre, celular ->
            val normalizado = TelefonoUtils.normalizar(celular)
            try {
                registrarCliente(
                    Cliente(
                        idCliente = normalizado,
                        nombre = nombre,
                        telefonoOriginal = celular,
                        origen = "manual"
                    )
                )
                true
            } catch (e: ErrorBd) {
                Snackbar.make(
                    binding.root,
                    e.message ?: getString(R.string.error_operacion),
                    Snackbar.LENGTH_LONG
                ).show()
                false
            }
        }
    }

    /** Nuevo → se inserta; inactivo → se reactiva con su historial; activo → se abre. */
    private fun registrarCliente(cliente: Cliente) {
        val existente = clienteDao.obtener(cliente.idCliente)
        when {
            existente == null -> {
                clienteDao.insertar(cliente)
                cargarClientes()
            }
            existente.estado == "inactivo" -> {
                clienteDao.reactivar(cliente)
                cargarClientes()
                aviso(getString(R.string.cliente_reactivado))
            }
            else -> {
                aviso(getString(R.string.cliente_existe))
                abrirDeudas(cliente.idCliente)
            }
        }
    }

    private fun editarNombre(cliente: ResumenCliente) {
        ClienteManualDialog.editarNombre(this, cliente.nombre) { nombre ->
            clienteDao.actualizarNombre(cliente.idCliente, nombre)
            cargarClientes()
        }
    }

    private fun compartirDeudas(cliente: ResumenCliente) {
        val deudas = deudaDao.listar(cliente.idCliente, "pendiente")
        if (deudas.isEmpty()) {
            aviso(getString(R.string.compartir_sin_deudas))
            return
        }

        val texto = ResumenUtils.construir(deudas)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.compartir_titulo, cliente.nombre))
            .setMessage(texto)
            .setPositiveButton(R.string.compartir_enviar) { _, _ ->
                val abierto = WhatsAppUtils.enviar(
                    this,
                    cliente.idCliente,
                    texto,
                    getString(R.string.compartir_chooser)
                )
                if (!abierto) aviso(getString(R.string.compartir_sin_whatsapp))
            }
            .setNeutralButton(R.string.compartir_copiar) { _, _ ->
                WhatsAppUtils.copiar(this, texto)
                aviso(getString(R.string.compartir_copiado))
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun confirmarEliminar(cliente: ResumenCliente) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cliente_eliminar_titulo)
            .setMessage(R.string.cliente_eliminar_mensaje)
            .setPositiveButton(R.string.accion_eliminar) { _, _ ->
                clienteDao.inactivar(cliente.idCliente)
                cargarClientes()
                aviso(getString(R.string.cliente_eliminado))
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun refrescarNombresDesdeContactos() {
        val estado = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
        if (estado != PackageManager.PERMISSION_GRANTED) return

        ejecutor.execute {
            val clientes = clienteDao.listarActivos().filter { it.origen == "contacto" }
            var cambios = false
            for (cliente in clientes) {
                val nombre = ContactosHelper.buscarNombre(this, cliente.idCliente) ?: continue
                if (nombre.isNotBlank() &&
                    clienteDao.actualizarNombreDesdeContacto(cliente.idCliente, nombre)
                ) {
                    cambios = true
                }
            }
            if (cambios) runOnUiThread { cargarClientes() }
        }
    }

    private fun programarRespaldo() {
        RespaldoScheduler.asegurar(this)
    }

    private fun abrirRespaldos() {
        val hoja = RespaldosBottomSheet()
        hoja.onRestaurar = { respaldo -> restaurarRespaldo(respaldo) }
        hoja.show(supportFragmentManager, "respaldos")
    }

    /**
     * Restaura un respaldo local: guarda una copia del estado actual, cierra la base y
     * reemplaza el archivo. La pantalla se recrea para abrir la base restaurada.
     */
    private fun restaurarRespaldo(respaldo: Respaldo) {
        try {
            if (getDatabasePath(DeudasDatabaseHelper.DATABASE_NAME).exists()) {
                RespaldoUtils.crear(this)
            }
            helper.close()
            RespaldoUtils.restaurar(this, respaldo)
            avisoPendiente = getString(R.string.respaldos_restaurado)
        } catch (e: Exception) {
            avisoPendiente = e.message ?: getString(R.string.respaldos_error)
        }
        recreate()
    }

    private fun aviso(mensaje: String) {
        AvisoUtils.mostrar(binding.root, mensaje, AvisoUtils.LARGO)
    }
}
