package com.mmq01.appdeudas.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmq01.appdeudas.Clases.Abono
import com.mmq01.appdeudas.Utils.FechaUtils
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.databinding.ItemAbonoBinding

class AdapterAbono(
    abonos: List<Abono>,
    private var soloLectura: Boolean,
    private val onAbonoEliminar: OnAbonoEliminar
) : RecyclerView.Adapter<AdapterAbono.AbonoViewHolder>() {

    private var lista: List<Abono> = abonos

    inner class AbonoViewHolder(val binding: ItemAbonoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbonoViewHolder {
        val binding = ItemAbonoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AbonoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AbonoViewHolder, position: Int) {
        val abono = lista[position]
        with(holder.binding) {
            tvValor.text = MonedaUtils.formatear(abono.valor)
            tvFecha.text = FechaUtils.diaLocal(abono.fecha)
            tvBadge.visibility = if (abono.tipo == "cierre") View.VISIBLE else View.GONE

            if (abono.nota.isNullOrBlank()) {
                tvNota.visibility = View.GONE
            } else {
                tvNota.visibility = View.VISIBLE
                tvNota.text = abono.nota
            }

            btnEliminar.visibility = if (soloLectura) View.GONE else View.VISIBLE
            btnEliminar.setOnClickListener { onAbonoEliminar.onAbonoEliminar(abono) }
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<Abono>, soloLectura: Boolean) {
        lista = nuevaLista
        this.soloLectura = soloLectura
        notifyDataSetChanged()
    }
}
