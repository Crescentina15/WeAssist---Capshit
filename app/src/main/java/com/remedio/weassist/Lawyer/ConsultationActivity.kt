package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R

class ConsultationActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        val clientName = intent.getStringExtra("client_name") ?: "Unknown Client"
        val consultationTime = intent.getStringExtra("consultation_time") ?: "Unknown Time"

        findViewById<TextView>(R.id.client_name_title).text = "Consultation with $clientName"
        findViewById<TextView>(R.id.consultation_time).text = consultationTime

        val consultationNotes = findViewById<EditText>(R.id.consultation_notes)
        val btnSave = findViewById<Button>(R.id.btn_save_consultation)

        database = FirebaseDatabase.getInstance().reference.child("consultations")

        btnSave.setOnClickListener {
            val notes = consultationNotes.text.toString().trim()
            if (notes.isEmpty()) {
                Toast.makeText(this, "Please enter consultation notes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val consultationId = database.push().key ?: return@setOnClickListener

            val consultationData = mapOf(
                "clientName" to clientName,
                "consultationTime" to consultationTime,
                "notes" to notes
            )

            database.child(clientName).child(consultationId).setValue(consultationData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Consultation saved successfully", Toast.LENGTH_SHORT).show()
                    consultationNotes.text.clear()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save consultation", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
