package com.remedio.weassist.Lawyer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R
import java.util.Locale

class ConsultationActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var consultationNotes: EditText
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    // Speech recognition request code
    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val speechResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = speechResults?.get(0) ?: ""

            // Append the spoken text to existing notes with proper spacing
            val currentPosition = consultationNotes.selectionStart
            val currentText = consultationNotes.text.toString()
            val newText = if (currentText.isEmpty() || currentText.endsWith("\n")) {
                currentText + spokenText
            } else {
                // Add a space if there's no space at the end of current text
                if (currentText.endsWith(" ")) {
                    currentText + spokenText
                } else {
                    "$currentText $spokenText"
                }
            }

            consultationNotes.setText(newText)
            // Place cursor at the end of text
            consultationNotes.setSelection(newText.length)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        // Check for audio recording permission
        checkRecordAudioPermission()

        auth = FirebaseAuth.getInstance()
        val clientName = intent.getStringExtra("client_name") ?: "Unknown Client"
        val consultationTime = intent.getStringExtra("consultation_time") ?: "Unknown Time"
        val consultationDate = intent.getStringExtra("date") ?: "Unknown Date"
        val problem = intent.getStringExtra("problem") ?: "" // Get problem from intent

        findViewById<TextView>(R.id.client_name_title).text = "Consultation with $clientName"
        findViewById<TextView>(R.id.consultation_time).text = "$consultationTime, $consultationDate"

        consultationNotes = findViewById(R.id.consultation_notes)
        val btnSave = findViewById<Button>(R.id.btn_save_consultation)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)

        // Setup speech-to-text floating action button
        setupSpeechToTextButton()

        btnBack.setOnClickListener {
            finish()
        }

        database = FirebaseDatabase.getInstance().reference.child("consultations")

        btnSave.setOnClickListener {
            val notes = consultationNotes.text.toString().trim()
            if (notes.isEmpty()) {
                Toast.makeText(this, "Please enter consultation notes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val consultationId = database.push().key ?: return@setOnClickListener
            val lawyerId = auth.currentUser?.uid ?: ""

            val consultationData = mapOf(
                "clientName" to clientName,
                "consultationTime" to consultationTime,
                "consultationDate" to consultationDate,
                "notes" to notes,
                "lawyerId" to lawyerId,
                "problem" to problem // Include problem in consultation data
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

    private fun checkRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio recording permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpeechToTextButton() {
        val fabSpeech: FloatingActionButton = findViewById(R.id.fab_speech_to_text)

        fabSpeech.setOnClickListener {
            startSpeechToText()
        }
    }

    private fun startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to transcribe")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            try {
                speechRecognitionLauncher.launch(speechIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            checkRecordAudioPermission()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}