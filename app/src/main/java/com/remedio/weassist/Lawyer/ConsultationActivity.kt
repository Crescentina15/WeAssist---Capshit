package com.remedio.weassist.Lawyer

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*

class ConsultationActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var clientNameTitle: TextView
    private lateinit var consultationTime: TextView
    private lateinit var problemDescriptionText: TextView
    private lateinit var noTranscriptText: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var transcriptText: TextView
    private lateinit var consultationNotes: EditText
    private lateinit var fabSpeechToText: FloatingActionButton
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    // Attachments section
    private lateinit var attachmentsCard: CardView
    private lateinit var tvNoAttachments: TextView
    private lateinit var recyclerViewAttachments: RecyclerView
    private lateinit var attachmentsAdapter: FileAttachmentAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private var appointmentId: String? = null
    private var clientName: String? = null
    private var problemDescription: String? = null
    private var attachments: List<String> = emptyList()

    // Speech recognition request code
    private val SPEECH_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize views
        initializeViews()

        // Setup attachment RecyclerView
        recyclerViewAttachments.layoutManager = LinearLayoutManager(this)
        attachmentsAdapter = FileAttachmentAdapter(ArrayList())
        recyclerViewAttachments.adapter = attachmentsAdapter

        // Get data from intent
        appointmentId = intent.getStringExtra("appointment_id")
        clientName = intent.getStringExtra("client_name")
        val consultationTimeStr = intent.getStringExtra("consultation_time")
        problemDescription = intent.getStringExtra("problem")
        val appointmentDate = intent.getStringExtra("date")

        // Set data to views
        clientNameTitle.text = "Consultation with $clientName"
        consultationTime.text = consultationTimeStr
        problemDescriptionText.text = problemDescription ?: "No problem description provided."

        // Check if the appointment already has notes
        loadExistingConsultation()

        // Load attachments
        loadAttachments()

        // Load transcript if available
        loadTranscript()

        // Set click listeners
        backButton.setOnClickListener { onBackPressed() }
        saveButton.setOnClickListener { saveConsultation() }
        fabSpeechToText.setOnClickListener { startSpeechToText() }
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.btn_back)
        clientNameTitle = findViewById(R.id.client_name_title)
        consultationTime = findViewById(R.id.consultation_time)
        problemDescriptionText = findViewById(R.id.problem_description_text)
        noTranscriptText = findViewById(R.id.no_transcript_text)
        transcriptScroll = findViewById(R.id.transcript_scroll)
        transcriptText = findViewById(R.id.transcript_text)
        consultationNotes = findViewById(R.id.consultation_notes)
        fabSpeechToText = findViewById(R.id.fab_speech_to_text)
        saveButton = findViewById(R.id.btn_save_consultation)
        progressBar = findViewById(R.id.progressBar)

        // Attachments views
        attachmentsCard = findViewById(R.id.attachments_card)
        tvNoAttachments = findViewById(R.id.tvNoAttachments)
        recyclerViewAttachments = findViewById(R.id.recyclerViewAttachments)
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to add notes")
        }

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speech recognition not available on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""

            // Append the spoken text to existing notes
            val currentNotes = consultationNotes.text.toString()
            val separator = if (currentNotes.isNotEmpty()) "\n" else ""
            consultationNotes.setText(currentNotes + separator + spokenText)

            // Move cursor to end
            consultationNotes.setSelection(consultationNotes.text.length)
        }
    }

    private fun loadAttachments() {
        if (appointmentId.isNullOrEmpty()) return

        progressBar.visibility = View.VISIBLE

        // First check in appointments node
        database.child("appointments").child(appointmentId!!)
            .child("attachments")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val fileList = snapshot.getValue<List<String>>()
                        if (fileList != null && fileList.isNotEmpty()) {
                            attachments = fileList
                            displayAttachments(fileList)
                        } else {
                            // If not found in appointments, check in accepted_appointment
                            checkAcceptedAppointmentForAttachments()
                        }
                    } else {
                        // If not found in appointments, check in accepted_appointment
                        checkAcceptedAppointmentForAttachments()
                    }
                    progressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConsultationActivity", "Error loading attachments: ${error.message}")
                    progressBar.visibility = View.GONE
                    checkAcceptedAppointmentForAttachments()
                }
            })
    }

    private fun checkAcceptedAppointmentForAttachments() {
        if (appointmentId.isNullOrEmpty()) {
            tvNoAttachments.visibility = View.VISIBLE
            recyclerViewAttachments.visibility = View.GONE
            return
        }

        database.child("accepted_appointment").child(appointmentId!!)
            .child("attachments")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    if (snapshot.exists()) {
                        val fileList = snapshot.getValue<List<String>>()
                        if (fileList != null && fileList.isNotEmpty()) {
                            attachments = fileList
                            displayAttachments(fileList)
                        } else {
                            checkLawyerAppointmentForAttachments()
                        }
                    } else {
                        checkLawyerAppointmentForAttachments()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Log.e(
                        "ConsultationActivity",
                        "Error checking accepted appointment: ${error.message}"
                    )
                    tvNoAttachments.visibility = View.VISIBLE
                    recyclerViewAttachments.visibility = View.GONE
                }
            })
    }

    private fun checkLawyerAppointmentForAttachments() {
        if (appointmentId.isNullOrEmpty()) {
            tvNoAttachments.visibility = View.VISIBLE
            recyclerViewAttachments.visibility = View.GONE
            return
        }

        val currentUser = auth.currentUser?.uid ?: return

        database.child("lawyers").child(currentUser)
            .child("appointments").child(appointmentId!!)
            .child("attachments")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    if (snapshot.exists()) {
                        val fileList = snapshot.getValue<List<String>>()
                        if (fileList != null && fileList.isNotEmpty()) {
                            attachments = fileList
                            displayAttachments(fileList)
                        } else {
                            tvNoAttachments.visibility = View.VISIBLE
                            recyclerViewAttachments.visibility = View.GONE
                        }
                    } else {
                        tvNoAttachments.visibility = View.VISIBLE
                        recyclerViewAttachments.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Log.e(
                        "ConsultationActivity",
                        "Error checking lawyer appointment: ${error.message}"
                    )
                    tvNoAttachments.visibility = View.VISIBLE
                    recyclerViewAttachments.visibility = View.GONE
                }
            })
    }

    private fun displayAttachments(fileList: List<String>) {
        if (fileList.isEmpty()) {
            tvNoAttachments.visibility = View.VISIBLE
            recyclerViewAttachments.visibility = View.GONE
        } else {
            tvNoAttachments.visibility = View.GONE
            recyclerViewAttachments.visibility = View.VISIBLE
            (attachmentsAdapter as FileAttachmentAdapter).updateFiles(fileList)
        }
    }

    private fun loadTranscript() {
        if (appointmentId.isNullOrEmpty()) return

        // Find conversation with this appointment ID
        database.child("conversations")
            .orderByChild("appointmentId")
            .equalTo(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (conversationSnapshot in snapshot.children) {
                            // Check if this conversation was forwarded from secretary
                            val forwardedFromSecretary =
                                conversationSnapshot.child("forwardedFromSecretary")
                                    .getValue(Boolean::class.java) ?: false
                            if (forwardedFromSecretary) {
                                // Get original conversation ID
                                val originalConversationId =
                                    conversationSnapshot.child("originalConversationId")
                                        .getValue(String::class.java)
                                if (!originalConversationId.isNullOrEmpty()) {
                                    loadSecretaryConversation(originalConversationId)
                                    return
                                }
                            }
                        }
                    }

                    // If we get here, no transcript was found
                    noTranscriptText.visibility = View.VISIBLE
                    transcriptScroll.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConsultationActivity", "Error loading conversation: ${error.message}")
                    noTranscriptText.visibility = View.VISIBLE
                    transcriptScroll.visibility = View.GONE
                }
            })
    }

    private fun loadSecretaryConversation(conversationId: String) {
        database.child("conversations").child(conversationId)
            .child("messages")
            .limitToLast(15) // Get last 15 messages
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        val messages = mutableListOf<MessagePreview>()

                        for (messageSnapshot in snapshot.children) {
                            val senderId =
                                messageSnapshot.child("senderId").getValue(String::class.java) ?: ""
                            val message =
                                messageSnapshot.child("message").getValue(String::class.java) ?: ""
                            val timestamp =
                                messageSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val senderName =
                                messageSnapshot.child("senderName").getValue(String::class.java)

                            messages.add(MessagePreview(senderId, message, timestamp, senderName))
                        }

                        // Sort by timestamp
                        messages.sortBy { it.timestamp }

                        // Format and display transcript
                        val builder = StringBuilder()
                        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

                        for (msg in messages) {
                            val sender = when {
                                msg.senderId == "system" -> "System"
                                msg.senderName != null -> msg.senderName
                                else -> "Unknown"
                            }

                            val time = dateFormat.format(Date(msg.timestamp))
                            builder.append("[$time] $sender: ${msg.message}\n\n")
                        }

                        if (builder.isNotEmpty()) {
                            transcriptText.text = builder.toString()
                            transcriptScroll.visibility = View.VISIBLE
                            noTranscriptText.visibility = View.GONE
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
                    Log.e(
                        "ConsultationActivity",
                        "Error loading secretary conversation: ${error.message}"
                    )
                    noTranscriptText.visibility = View.VISIBLE
                    transcriptScroll.visibility = View.GONE
                }
            })
    }

    private data class MessagePreview(
        val senderId: String = "",
        val message: String = "",
        val timestamp: Long = 0,
        val senderName: String? = null
    )

    private fun loadExistingConsultation() {
        if (appointmentId.isNullOrEmpty() || clientName.isNullOrEmpty()) return

        progressBar.visibility = View.VISIBLE

        // Try to find existing consultation
        val consultationsRef = database.child("consultations")
        consultationsRef.orderByChild("appointmentId").equalTo(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    if (snapshot.exists()) {
                        for (childSnapshot in snapshot.children) {
                            for (consultationSnapshot in childSnapshot.children) {
                                val consultation =
                                    consultationSnapshot.getValue(Consultation::class.java)
                                if (consultation != null) {
                                    consultationNotes.setText(consultation.notes)
                                    break
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Log.e("ConsultationActivity", "Error loading consultation: ${error.message}")
                }
            })
    }

    private fun saveConsultation() {
        if (clientName.isNullOrEmpty() || appointmentId.isNullOrEmpty()) {
            Toast.makeText(this, "Missing required information", Toast.LENGTH_SHORT).show()
            return
        }

        val notes = consultationNotes.text.toString().trim()
        val lawyerId = auth.currentUser?.uid ?: return
        val currentDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

        // Create consultation object
        val consultation = Consultation(
            clientName = clientName!!,
            consultationTime = consultationTime.text.toString(),
            notes = notes,
            lawyerId = lawyerId,
            consultationDate = currentDate,
            consultationType = "Legal Consultation",
            status = "Completed",
            problem = problemDescription ?: "",
            appointmentId = appointmentId!!
        )

        progressBar.visibility = View.VISIBLE

        // Save to Firebase
        val clientKey = clientName!!.replace(" ", "_").lowercase()
        val consultationKey = database.child("consultations").child(clientKey).push().key ?: return
        database.child("consultations").child(clientKey).child(consultationKey)
            .setValue(consultation)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Consultation notes saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("ConsultationActivity", "Error saving consultation: ${e.message}")
                Toast.makeText(this, "Failed to save consultation notes", Toast.LENGTH_SHORT).show()
            }
    }

    // File Adapter class for attachments
    inner class FileAttachmentAdapter(
        private var files: List<String>
    ) : RecyclerView.Adapter<FileAttachmentAdapter.FileViewHolder>() {

        fun updateFiles(newFiles: List<String>) {
            files = newFiles
            notifyDataSetChanged()
        }

        inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileName: TextView = view.findViewById(R.id.tvFileName)
            val fileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
            val downloadIcon: ImageView = view.findViewById(R.id.ivDownload)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_consultation, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val fileUrl = files[position]
            holder.fileName.text = getFileNameFromUrl(fileUrl)

            // Set the appropriate icon based on file type
            val extension = fileUrl.substringAfterLast('.', "").lowercase()
            val iconResource = when {
                extension.matches(Regex("jpe?g|png|gif|bmp")) -> R.drawable.ic_image
                extension == "pdf" -> R.drawable.ic_pdf
                extension.matches(Regex("doc|docx")) -> R.drawable.ic_file
                extension.matches(Regex("xls|xlsx")) -> R.drawable.ic_file
                extension.matches(Regex("ppt|pptx")) -> R.drawable.ic_file
                extension.matches(Regex("mp4|avi|mov|wmv")) -> R.drawable.ic_video
                extension.matches(Regex("mp3|wav|ogg")) -> R.drawable.ic_file
                else -> R.drawable.ic_file
            }
            holder.fileIcon.setImageResource(iconResource)

            // Set click listeners
            holder.itemView.setOnClickListener { handleFileClick(fileUrl) }
            holder.downloadIcon.setOnClickListener { downloadFile(fileUrl) }
        }

        override fun getItemCount() = files.size
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            url.substring(url.lastIndexOf('/') + 1).substringBefore('?')
        } catch (e: Exception) {
            "file_${System.currentTimeMillis()}"
        }
    }

    private fun handleFileClick(url: String) {
        try {
            val extension = url.substringAfterLast('.', "").lowercase()
            if (extension.matches(Regex("jpe?g|png|gif|bmp"))) {
                // For images, view them
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(url), getMimeType(url))
                startActivity(intent)
            } else {
                // For all other file types, download them
                downloadFile(url)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadFile(url: String) {
        try {
            val fileName = getFileNameFromUrl(url)
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(fileName)
            request.setDescription("Downloading $fileName")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DownloadError", "Error downloading file: ${e.message}")
            Toast.makeText(this, "Failed to download file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(url: String): String {
        val extension = url.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "*/*"
        }
    }
}