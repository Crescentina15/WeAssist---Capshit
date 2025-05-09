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
import androidx.cardview.widget.CardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConsultationActivity : AppCompatActivity() {

    private lateinit var clientNameTitle: TextView
    private lateinit var consultationTime: TextView
    private lateinit var consultationNotes: EditText
    private lateinit var speechToTextFab: FloatingActionButton
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageButton

    // Transcript views
    private lateinit var transcriptCard: CardView
    private lateinit var noTranscriptText: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var transcriptText: TextView

    private var clientId: String = ""
    private var appointmentId: String = ""
    private var problem: String = ""
    private var date: String = ""
    private var clientName: String = ""
    private var conversationId: String? = null

    // Update the onCreate method to remove the prefilled text in the consultation notes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        // Initialize views
        clientNameTitle = findViewById(R.id.client_name_title)
        consultationTime = findViewById(R.id.consultation_time)
        consultationNotes = findViewById(R.id.consultation_notes)
        speechToTextFab = findViewById(R.id.fab_speech_to_text)
        saveButton = findViewById(R.id.btn_save_consultation)
        backButton = findViewById(R.id.btn_back)

        // Initialize transcript views
        transcriptCard = findViewById(R.id.transcript_card)
        noTranscriptText = findViewById(R.id.no_transcript_text)
        transcriptScroll = findViewById(R.id.transcript_scroll)
        transcriptText = findViewById(R.id.transcript_text)

        // Get intent extras
        clientName = intent.getStringExtra("client_name") ?: "Client"
        val time = intent.getStringExtra("consultation_time") ?: ""
        appointmentId = intent.getStringExtra("appointment_id") ?: ""
        problem = intent.getStringExtra("problem") ?: ""
        date = intent.getStringExtra("date") ?: ""

        // Set UI
        clientNameTitle.text = "Consultation with $clientName"
        consultationTime.text = time

        // Set problem description
        val problemDescriptionText = findViewById<TextView>(R.id.problem_description_text)
        problemDescriptionText.text = problem.ifEmpty { "No problem description provided." }

        // No prefilled notes text anymore

        // Set click listeners
        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveConsultation() }

        // Find client ID for the appointment
        findClientId()
    }

    private fun findClientId() {
        if (appointmentId.isEmpty()) {
            noTranscriptText.visibility = View.VISIBLE
            transcriptScroll.visibility = View.GONE
            return
        }

        val database = FirebaseDatabase.getInstance().reference
        database.child("appointments").child(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        clientId = snapshot.child("clientId").getValue(String::class.java) ?: ""

                        if (clientId.isNotEmpty()) {
                            findClientSecretaryConversation()
                        } else {
                            noTranscriptText.visibility = View.VISIBLE
                            transcriptScroll.visibility = View.GONE
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConsultationActivity", "Error finding client ID: ${error.message}")
                }
            })
    }

    private fun findClientSecretaryConversation() {
        if (clientId.isEmpty()) {
            noTranscriptText.visibility = View.VISIBLE
            transcriptScroll.visibility = View.GONE
            return
        }

        val database = FirebaseDatabase.getInstance().reference

        // First check for conversations by appointmentId
        database.child("conversations")
            .orderByChild("appointmentId")
            .equalTo(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundSecretaryConversation = false

                    for (conversationSnapshot in snapshot.children) {
                        // Look for secretary conversations
                        if (conversationSnapshot.child("forwardedFromSecretary").getValue(Boolean::class.java) == true) {
                            // We found a forwarded conversation, check for original
                            val originalConversationId = conversationSnapshot.child("originalConversationId")
                                .getValue(String::class.java)

                            if (originalConversationId != null) {
                                conversationId = originalConversationId
                                foundSecretaryConversation = true
                                loadConversationPreview(originalConversationId)
                                break
                            }
                        } else if (!conversationSnapshot.child("handledByLawyer").exists() ||
                            conversationSnapshot.child("handledByLawyer").getValue(Boolean::class.java) == false) {
                            // This is likely a secretary conversation
                            conversationId = conversationSnapshot.key
                            foundSecretaryConversation = true
                            loadConversationPreview(conversationSnapshot.key!!)
                            break
                        }
                    }

                    if (!foundSecretaryConversation) {
                        findConversationByClientId()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConsultationActivity", "Error finding conversation: ${error.message}")
                    noTranscriptText.visibility = View.VISIBLE
                    transcriptScroll.visibility = View.GONE
                }
            })
    }

    private fun findConversationByClientId() {
        // Try to find a conversation between client and any secretary
        val database = FirebaseDatabase.getInstance().reference

        database.child("conversations")
            .orderByChild("participantIds/$clientId")
            .equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundSecretaryConversation = false

                    for (conversationSnapshot in snapshot.children) {
                        // Skip if this is a lawyer conversation
                        if (conversationSnapshot.child("handledByLawyer").getValue(Boolean::class.java) == true) {
                            continue
                        }

                        // Look for secretary ID in the participants
                        val participantIds = conversationSnapshot.child("participantIds")

                        for (participantSnapshot in participantIds.children) {
                            val participantId = participantSnapshot.key ?: continue

                            // Check if this participant is a secretary
                            isSecretaryAccount(participantId) { isSecretary ->
                                if (isSecretary) {
                                    conversationId = conversationSnapshot.key
                                    foundSecretaryConversation = true
                                    loadConversationPreview(conversationSnapshot.key!!)
                                    return@isSecretaryAccount
                                }
                            }

                            if (foundSecretaryConversation) break
                        }

                        if (foundSecretaryConversation) break
                    }

                    if (!foundSecretaryConversation) {
                        // No secretary conversation found
                        noTranscriptText.visibility = View.VISIBLE
                        transcriptScroll.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConsultationActivity", "Error finding conversation: ${error.message}")
                    noTranscriptText.visibility = View.VISIBLE
                    transcriptScroll.visibility = View.GONE
                }
            })
    }

    private fun isSecretaryAccount(userId: String, callback: (Boolean) -> Unit) {
        val database = FirebaseDatabase.getInstance().reference

        database.child("secretaries").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.exists())
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(false)
                }
            })
    }

    private fun loadConversationPreview(conversationId: String) {
        val database = FirebaseDatabase.getInstance().reference

        database.child("conversations").child(conversationId).child("messages")
            .limitToLast(5)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        val messages = mutableListOf<Message>()

                        for (messageSnapshot in snapshot.children) {
                            val message = messageSnapshot.getValue(Message::class.java)
                            if (message != null) {
                                messages.add(message)
                            }
                        }

                        if (messages.isNotEmpty()) {
                            showConversationPreview(messages)
                        } else {
                            noTranscriptText.visibility = View.VISIBLE
                            transcriptScroll.visibility = View.GONE
                        }
                    } else {
                        noTranscriptText.visibility = View.VISIBLE
                        transcriptScroll.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConsultationActivity", "Error loading messages: ${error.message}")
                    noTranscriptText.visibility = View.VISIBLE
                    transcriptScroll.visibility = View.GONE
                }
            })
    }

    private fun showConversationPreview(messages: List<Message>) {
        if (messages.isEmpty()) {
            noTranscriptText.visibility = View.VISIBLE
            transcriptScroll.visibility = View.GONE
            return
        }

        val preview = StringBuilder()
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        // Get names for the participants
        val participantNames = mutableMapOf<String, String>()

        // Add default names
        participantNames[clientId] = clientName

        for (message in messages) {
            // Skip system messages in preview
            if (message.senderId == "system") continue

            val senderName = when (message.senderId) {
                clientId -> clientName
                "system" -> "System"
                else -> message.senderName ?: "Secretary"
            }

            val timestamp = dateFormat.format(Date(message.timestamp))
            preview.append("$senderName ($timestamp): ${message.message}\n\n")
        }

        if (preview.isNotEmpty()) {
            transcriptText.text = preview.toString()
            noTranscriptText.visibility = View.GONE
            transcriptScroll.visibility = View.VISIBLE
        } else {
            noTranscriptText.visibility = View.VISIBLE
            transcriptScroll.visibility = View.GONE
        }
    }

    private fun saveConsultation() {
        val notes = consultationNotes.text.toString().trim()

        if (notes.isEmpty()) {
            Toast.makeText(this, "Please enter consultation notes", Toast.LENGTH_SHORT).show()
            return
        }

        val lawyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val consultation = Consultation(
            clientName = clientName,
            consultationTime = consultationTime.text.toString(),
            notes = notes,
            lawyerId = lawyerId,
            consultationDate = date,
            consultationType = "Office Consultation",
            status = "Completed",
            problem = problem
        )

        // Save the consultation data to Firebase
        val database = FirebaseDatabase.getInstance().reference
        val consultationKey = database.child("consultations").child(clientId).push().key

        if (consultationKey != null) {
            database.child("consultations").child(clientId).child(consultationKey)
                .setValue(consultation)
                .addOnSuccessListener {
                    Toast.makeText(this, "Consultation logs saved successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save consultation logs: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}