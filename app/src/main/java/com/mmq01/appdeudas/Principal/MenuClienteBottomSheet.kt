package com.mmq01.appdeudas.Principal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmq01.appdeudas.databinding.SheetMenuClienteBinding

class MenuClienteBottomSheet : BottomSheetDialogFragment() {

    var onDesdeContactos: (() -> Unit)? = null
    var onManual: (() -> Unit)? = null

    private var _binding: SheetMenuClienteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetMenuClienteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.filaContactos.setOnClickListener {
            dismiss()
            onDesdeContactos?.invoke()
        }
        binding.filaManual.setOnClickListener {
            dismiss()
            onManual?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
