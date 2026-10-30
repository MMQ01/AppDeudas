package com.mmq01.appdeudas.Utils

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.Window

class LoadingDialog(private val context: Context) {

    private var dialog: Dialog? = null

    fun show() {
        if (dialog?.isShowing == true) return

        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setContentView(
                LayoutInflater.from(context).inflate(
                    com.mmq01.appdeudas.R.layout.dialog_loader,
                    null
                )
            )
            show()
        }
    }

    fun hide() {
        dialog?.dismiss()
        dialog = null
    }
}
