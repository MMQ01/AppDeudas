package com.mmq01.appdeudas.Adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mmq01.appdeudas.Clases.DeudaDetalle
import com.mmq01.appdeudas.R
import com.mmq01.appdeudas.Utils.FechaUtils
import com.mmq01.appdeudas.Utils.MonedaUtils
import com.mmq01.appdeudas.databinding.ItemDeudaBinding

class AdapterDeuda(
    deudas: List<DeudaDetalle>,
    private val onDeudaAccion: OnDeudaAccion
) : RecyclerView.Adapter<AdapterDeuda.DeudaViewHolder>() {

    private var lista: List<DeudaDetalle> = deudas

    inner class DeudaViewHolder(val binding: ItemDeudaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeudaViewHolder {
        val binding = ItemDeudaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeudaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeudaViewHolder, position: Int) {
        val deuda = lista[position]
        val contexto = holder.binding.root.context
        with(holder.binding) {
            tvNombre.text = deuda.nombre

            if (deuda.descripcion.isNullOrBlank()) {
                tvDescripcion.visibility = View.GONE
            } else {
                tvDescripcion.visibility = View.VISIBLE
                tvDescripcion.text = deuda.descripcion
            }

            tvFecha.text = contexto.getString(
                R.string.fecha_deuda_etiqueta,
                FechaUtils.diaLocal(deuda.fechaCreacion)
            )

            tvValor.text = contexto.getString(
                R.string.valor_etiqueta,
                MonedaUtils.formatear(deuda.valor)
            )
            tvSaldo.text = contexto.getString(
                R.string.saldo_etiqueta,
                MonedaUtils.formatear(deuda.saldo)
            )

            tvBadge.text = when (deuda.estado) {
                "pagada" -> contexto.getString(R.string.estado_pagada)
                "anulada" -> contexto.getString(R.string.estado_anulada)
                else -> contexto.getString(R.string.estado_pendiente)
            }
            val colorBadge = when (deuda.estado) {
                "pagada" -> R.color.color_pagada
                "anulada" -> R.color.color_anulada
                else -> R.color.color_pendiente
            }
            tvBadge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(contexto, colorBadge)
            )

            progress.max = 1000
            progress.progress = if (deuda.valor > 0) {
                (((deuda.valor - deuda.saldo) / deuda.valor) * 1000).toInt().coerceIn(0, 1000)
            } else {
                0
            }

            if (deuda.estado == "pagada" && !deuda.fechaPago.isNullOrBlank()) {
                tvFechaPago.visibility = View.VISIBLE
                tvFechaPago.text = contexto.getString(
                    R.string.fecha_pago_etiqueta,
                    FechaUtils.diaLocal(deuda.fechaPago)
                )
            } else {
                tvFechaPago.visibility = View.GONE
            }

            root.setOnClickListener { onDeudaAccion.onDeudaClick(deuda) }
            btnMenu.setOnClickListener { onDeudaAccion.onDeudaMenu(deuda, it) }
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<DeudaDetalle>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}
