package com.remedio.weassist

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SecurityActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        // Back button functionality
        findViewById<ImageButton>(R.id.back_arrow)?.setOnClickListener {
            finish()
        }
    }
}
