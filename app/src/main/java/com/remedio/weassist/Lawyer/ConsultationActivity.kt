package com.remedio.weassist.Lawyer

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.remedio.weassist.R

class ConsultationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        val clientName = intent.getStringExtra("client_name") ?: "Unknown Client"
        val consultationTime = intent.getStringExtra("consultation_time") ?: "Unknown Time"

        findViewById<TextView>(R.id.client_name_title).text = "Consultation with $clientName"
        findViewById<TextView>(R.id.consultation_time).text = consultationTime
    }
}
