package com.mmq01.appdeudas.Adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmq01.appdeudas.Clases.LineaReparto
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.Utils.RepartoUtils
import com.mmq01.appdeudas.databinding.ItemRepartoBinding

/** Líneas del reparto de un abono general; el monto de cada una se puede editar a mano. */
class AdapterReparto(
    private val alCambiar: () -> Unit
) : RecyclerView.Adapter<AdapterReparto.RepartoViewHolder>() {

    private var lineas: MutableList<LineaReparto> = mutableListOf()

    /** Líneas con los montos tal como están ahora (incluye lo editado). */
    val actuales: List<LineaReparto> get() = lineas

    inner class RepartoViewHolder(val binding: ItemRepartoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var watcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepartoViewHolder {
        val binding = ItemRepartoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RepartoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RepartoViewHolder, position: Int) {
        val linea = lineas[position]
        with(holder.binding) {
            val contexto = root.context
            tvNombre.text = linea.nombre
            tvDetalle.text = contexto.getString(
                R.string.pg_detalle_linea,
                MonedaUtils.formatear(linea.saldo.toDouble()),
                RepartoUtils.fechaCorta(linea.fechaCreacion)
            )
            tvBadge.visibility = if (linea.quedaPagada) View.VISIBLE else View.GONE

            holder.watcher?.let { etMonto.removeTextChangedListener(it) }
            etMonto.setText(if (linea.monto > 0) linea.monto.toString() else "")
            val nuevo = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val monto = s?.toString()?.toLongOrNull() ?: 0L
                    lineas[pos] = lineas[pos].copy(monto = monto)
                    tvBadge.visibility = if (lineas[pos].quedaPagada) View.VISIBLE else View.GONE
                    alCambiar()
                }
            }
            etMonto.addTextChangedListener(nuevo)
            holder.watcher = nuevo
        }
    }

    override fun getItemCount(): Int = lineas.size

    fun actualizarLineas(nuevas: List<LineaReparto>) {
        lineas = nuevas.toMutableList()
        notifyDataSetChanged()
    }
}
