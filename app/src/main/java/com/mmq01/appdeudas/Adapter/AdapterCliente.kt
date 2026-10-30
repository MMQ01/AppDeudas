package com.mmq01.appdeudas.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmq01.appdeudas.Clases.ResumenCliente
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.Utils.TelefonoUtils
import com.mmq01.appdeudas.databinding.ItemClienteBinding
import java.text.Normalizer
import java.util.Locale

class AdapterCliente(
    clientes: List<ResumenCliente>,
    private val onClienteClick: OnClienteClick,
    private val onClienteLongClick: OnClienteLongClick
) : RecyclerView.Adapter<AdapterCliente.ClienteViewHolder>() {

    private var listaOriginal: List<ResumenCliente> = clientes
    private var listaFiltrada: List<ResumenCliente> = clientes

    inner class ClienteViewHolder(val binding: ItemClienteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val binding = ItemClienteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClienteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = listaFiltrada[position]
        with(holder.binding) {
            tvNombre.text = cliente.nombre
            tvTelefono.text = TelefonoUtils.paraMostrar(cliente.telefonoOriginal ?: cliente.idCliente)
            val pendientes = root.context.getString(R.string.pendientes_cantidad, cliente.deudasPendientes)
            tvPendientes.text = if (cliente.saldoFavor > 0) {
                pendientes + " · " + root.context.getString(
                    R.string.a_favor_etiqueta,
                    MonedaUtils.formatear(cliente.saldoFavor)
                )
            } else {
                pendientes
            }
            tvTotal.text = MonedaUtils.formatear(cliente.saldoPendiente)
            root.setOnClickListener { onClienteClick.onClienteClick(cliente) }
            root.setOnLongClickListener { vista ->
                onClienteLongClick.onClienteLongClick(cliente, vista)
                true
            }
        }
    }

    override fun getItemCount(): Int = listaFiltrada.size

    fun actualizarLista(nuevaLista: List<ResumenCliente>, textoFiltro: String = "") {
        listaOriginal = nuevaLista
        aplicarFiltro(textoFiltro)
    }

    fun filtrar(texto: String) {
        aplicarFiltro(texto)
    }

    private fun aplicarFiltro(texto: String) {
        val buscado = normalizar(texto)
        val digitos = texto.filter(Char::isDigit)
        listaFiltrada = if (buscado.isBlank()) {
            listaOriginal
        } else {
            listaOriginal.filter { cliente ->
                normalizar(cliente.nombre).contains(buscado) ||
                    (digitos.isNotEmpty() && cliente.idCliente.contains(digitos))
            }
        }
        notifyDataSetChanged()
    }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .uppercase(Locale.getDefault())
}
