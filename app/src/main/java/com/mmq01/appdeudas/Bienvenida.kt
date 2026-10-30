package com.mmq01.appdeudas

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.mmq01.appdeudas.Principal.ClientesActivity

class Bienvenida : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bienvenida)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ClientesActivity::class.java))
            finish()
        }, 2000)
    }
}
