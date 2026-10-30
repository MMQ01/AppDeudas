package com.mmq01.appdeudas.Adapter

import android.view.View
import com.mmq01.appdeudas.Clases.Abono
import com.mmq01.appdeudas.Clases.DeudaDetalle
import com.mmq01.appdeudas.Clases.ResumenCliente

interface OnClienteClick {
    fun onClienteClick(cliente: ResumenCliente)
}

interface OnClienteLongClick {
    fun onClienteLongClick(cliente: ResumenCliente, ancla: View)
}

interface OnDeudaAccion {
    fun onDeudaClick(deuda: DeudaDetalle)
    fun onDeudaMenu(deuda: DeudaDetalle, ancla: View)
}

interface OnAbonoEliminar {
    fun onAbonoEliminar(abono: Abono)
}
