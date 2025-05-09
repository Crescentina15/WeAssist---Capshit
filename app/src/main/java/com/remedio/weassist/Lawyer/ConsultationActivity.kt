package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConsultationActivity : AppCompatActivity() {

    private lateinit var clientNameTitle: TextView
    private lateinit var consultationTime: TextView
    private lateinit var problemDescriptionText: TextView
    private lateinit var noTranscriptText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var consultationNotes: EditText
    private lateinit var saveButton: Button
    private lateinit var btnBack: ImageButton
    private lateinit var fabSpeechToText: FloatingActionButton

    private lateinit var clientName: String
    private lateinit var problem: String
    private lateinit var date: String
    private var clientId: String = ""
    private var appointmentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        // Initialize views
        clientNameTitle = findViewById(R.id.client_name_title)
        consultationTime = findViewById(R.id.consultation_time)
        problemDescriptionText = findViewById(R.id.problem_description_text)
        noTranscriptText = findViewById(R.id.no_transcript_text)
        transcriptText = findViewById(R.id.transcript_text)
        transcriptScroll = findViewById(R.id.transcript_scroll)
        consultationNotes = findViewById(R.id.consultation_notes)
        saveButton = findViewById(R.id.btn_save_consultation)
        btnBack = findViewById(R.id.btn_back)
        fabSpeechToText = findViewById(R.id.fab_speech_to_text)

        // Get data from intent
        clientName = intent.getStringExtra("client_name") ?: "Client"
        val consultationTimeStr = intent.getStringExtra("consultation_time") ?: ""
        problem = intent.getStringExtra("problem") ?: "No problem description provided."
        appointmentId = intent.getStringExtra("appointment_id") ?: ""
        clientId = intent.getStringExtra("client_id") ?: ""
        date = intent.getStringExtra("date") ?: SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())

        // Log for debugging
        Log.d("ConsultationActivity", "Appointment ID: $appointmentId")
        Log.d("ConsultationActivity", "Client ID: $clientId")
        Log.d("ConsultationActivity", "Client Name: $clientName")
        Log.d("ConsultationActivity", "Problem: $problem")

        // If client ID wasn't passed, try to get it from the appointment
        if (clientId.isEmpty() && appointmentId.isNotEmpty()) {
            fetchClientIdFromAppointment(appointmentId)
        }

        // Set up UI
        clientNameTitle.text = "Consultation with $clientName"
        consultationTime.text = consultationTimeStr.ifEmpty { "Time not specified" }
        problemDescriptionText.text = problem

        // Check if there's a conversation transcript
        checkForConversationTranscript()

        // Set up buttons
        saveButton.setOnClickListener {
            saveConsultation()
        }

        btnBack.setOnClickListener {
            finish()
        }

        // Set up speech-to-text functionality
        setupSpeechToText()

        // Check if we should load previous consultation data
        if (appointmentId.isNotEmpty()) {
            loadPreviousConsultationNotes(appointmentId)
        }
    }

    private fun setupSpeechToText() {
        fabSpeechToText.setOnClickListener {
            // Implement speech-to-text functionality
            // This is a placeholder - you would need to implement the actual speech recognition
            Toast.makeText(this, "Speech to text feature not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkForConversationTranscript() {
        if (appointmentId.isNotEmpty()) {
            val conversationsRef = FirebaseDatabase.getInstance().reference.child("conversations")
            conversationsRef.orderByChild("appointmentId").equalTo(appointmentId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // Found conversation related to this appointment
                            var transcriptFound = false
                            for (conversationSnapshot in snapshot.children) {
                                // Check if this has a secretary involved
                                val secretaryActive = conversationSnapshot.child("secretaryActive").getValue(Boolean::class.java) ?: false
                                val forwarded = conversationSnapshot.child("forwarded").getValue(Boolean::class.java) ?: false

                                if (secretaryActive || forwarded) {
                                    // Get a sample of messages
                                    val messagesSnapshot = conversationSnapshot.child("messages")
                                    if (messagesSnapshot.exists() && messagesSnapshot.childrenCount > 0) {
                                        val messageBuilder = StringBuilder()
                                        var messageCount = 0

                                        // Get up to 5 most recent non-system messages
                                        messagesSnapshot.children.forEach { messageNode ->
                                            val senderId = messageNode.child("senderId").getValue(String::class.java)
                                            if (senderId != "system" && messageCount < 5) {
                                                val message = messageNode.child("message").getValue(String::class.java) ?: ""
                                                val senderName = messageNode.child("senderName").getValue(String::class.java) ?: "Unknown"

                                                messageBuilder.append("$senderName: $message\n\n")
                                                messageCount++
                                            }
                                        }

                                        if (messageCount > 0) {
                                            transcriptFound = true
                                            noTranscriptText.visibility = View.GONE
                                            transcriptScroll.visibility = View.VISIBLE
                                            transcriptText.text = messageBuilder.toString()
                                            break
                                        }
                                    }
                                }
                            }

                            if (!transcriptFound) {
                                noTranscriptText.text = "No relevant messages found in conversation"
                            }
                        } else {
                            noTranscriptText.text = "No prior conversation found for this appointment"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("ConsultationActivity", "Error fetching conversation: ${error.message}")
                        noTranscriptText.text = "Error loading conversation data"
                    }
                })
        } else {
            noTranscriptText.text = "No appointment ID available to find conversation"
        }
    }

    private fun fetchClientIdFromAppointment(appointmentId: String) {
        val appointmentRef = FirebaseDatabase.getInstance().reference
            .child("appointments").child(appointmentId)

        appointmentRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    clientId = snapshot.child("clientId").getValue(String::class.java) ?: ""
                    Log.d("ConsultationActivity", "Fetched client ID: $clientId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ConsultationActivity", "Error fetching client ID: ${error.message}")
            }
        })
    }

    private fun loadPreviousConsultationNotes(appointmentId: String) {
        // Try to load existing consultation for this appointment
        val consultationsRef = FirebaseDatabase.getInstance().reference.child("consultations")

        consultationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    var foundExistingNotes = false

                    // Search through all client nodes
                    for (clientSnapshot in snapshot.children) {
                        for (consultationSnapshot in clientSnapshot.children) {
                            val storedAppointmentId = consultationSnapshot.child("appointmentId").getValue(String::class.java)

                            if (storedAppointmentId == appointmentId) {
                                // Found existing consultation for this appointment
                                val notes = consultationSnapshot.child("notes").getValue(String::class.java) ?: ""
                                if (notes.isNotEmpty()) {
                                    consultationNotes.setText(notes)
                                    foundExistingNotes = true
                                    Toast.makeText(this@ConsultationActivity,
                                        "Loaded existing consultation notes",
                                        Toast.LENGTH_SHORT).show()
                                }
                                break
                            }
                        }
                        if (foundExistingNotes) break
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ConsultationActivity", "Error loading previous notes: ${error.message}")
            }
        })
    }

    private fun saveConsultation() {
        val notes = consultationNotes.text.toString().trim()

        if (notes.isEmpty()) {
            Toast.makeText(this, "Please enter consultation notes", Toast.LENGTH_SHORT).show()
            return
        }

        val lawyerId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (lawyerId.isEmpty()) {
            Toast.makeText(this, "Error: Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // If clientId is still empty, use a fallback method
        val finalClientId = if (clientId.isEmpty()) {
            // Generate a client ID from the name if we don't have one
            val sanitizedName = clientName.replace(" ", "_").lowercase()
            "client_${sanitizedName}_${System.currentTimeMillis()}"
        } else {
            clientId
        }

        // Log for debugging
        Log.d("ConsultationSave", "Saving consultation with appointmentId: $appointmentId")
        Log.d("ConsultationSave", "Client ID being used: $finalClientId")

        val consultation = Consultation(
            clientName = clientName,
            consultationTime = consultationTime.text.toString(),
            notes = notes,
            lawyerId = lawyerId,
            consultationDate = date,
            consultationType = "Office Consultation",
            status = "Completed",
            problem = problem,
            appointmentId = appointmentId  // This is the critical fix
        )

        // Save the consultation data to Firebase
        val database = FirebaseDatabase.getInstance().reference
        val consultationKey = database.child("consultations").child(finalClientId).push().key

        if (consultationKey != null) {
            database.child("consultations").child(finalClientId).child(consultationKey)
                .setValue(consultation)
                .addOnSuccessListener {
                    // Update the appointment status to Completed if needed
                    updateAppointmentStatus()

                    Toast.makeText(this, "Consultation logs saved successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save consultation logs: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Failed to generate key for consultation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAppointmentStatus() {
        if (appointmentId.isNotEmpty()) {
            // Update status in appointments node
            val appointmentRef = FirebaseDatabase.getInstance().reference
                .child("appointments").child(appointmentId)

            appointmentRef.child("status").setValue("Complete")
                .addOnSuccessListener {
                    Log.d("ConsultationActivity", "Updated appointment status to Complete")
                }
                .addOnFailureListener { e ->
                    Log.e("ConsultationActivity", "Failed to update appointment status: ${e.message}")
                }

            // Also update in accepted_appointment node if it exists
            val acceptedAppointmentRef = FirebaseDatabase.getInstance().reference
                .child("accepted_appointment").child(appointmentId)

            acceptedAppointmentRef.child("status").setValue("Complete")
        }
    }
}