package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.R
import com.remedio.weassist.Utils.ConversationUtils

class SecretaryAppointmentDetailsActivity : AppCompatActivity() {

    private lateinit var tvAppointmentTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAppointmentDate: TextView
    private lateinit var tvAppointmentTime: TextView
    private lateinit var tvLawyerName: TextView
    private lateinit var tvSecretaryName: TextView
    private lateinit var tvProblemDescription: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAccept: Button
    private lateinit var btnDecline: Button
    private lateinit var btnForwardToLawyer: Button
    private lateinit var cardViewActions: CardView
    private lateinit var cardViewForward: CardView
    private lateinit var database: DatabaseReference
    private var currentSecretaryName: String = "Secretary"
    private var currentAppointment: Appointment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_appointment_details)

        // Initialize database
        database = FirebaseDatabase.getInstance().reference

        // Initialize views
        tvAppointmentTitle = findViewById(R.id.tvAppointmentTitle)
        tvStatus = findViewById(R.id.tvStatus)
        tvAppointmentDate = findViewById(R.id.tvAppointmentDate)
        tvAppointmentTime = findViewById(R.id.tvAppointmentTime)
        tvLawyerName = findViewById(R.id.tvLawyerName)
        tvSecretaryName = findViewById(R.id.tvSecretaryName)
        tvProblemDescription = findViewById(R.id.tvProblemDescription)
        progressBar = findViewById(R.id.progressBar)
        btnAccept = findViewById(R.id.btnAccept)
        btnDecline = findViewById(R.id.btnDecline)
        cardViewActions = findViewById(R.id.cardViewActions)

        // Initialize forward button and card
        btnForwardToLawyer = findViewById(R.id.btnForwardToLawyer)
        cardViewForward = findViewById(R.id.cardViewForward)

        // Initially hide the forward option
        cardViewForward.visibility = View.GONE

        // Set up back button
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Get appointment ID from intent
        val appointmentId = intent.getStringExtra("APPOINTMENT_ID")
        if (appointmentId != null) {
            loadAppointmentDetails(appointmentId)
            fetchCurrentSecretaryName()
        } else {
            Toast.makeText(this, "Error: Appointment ID not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Set up accept and decline buttons
        btnAccept.setOnClickListener {
            appointmentId?.let { id -> updateAppointmentStatus(id, "Accepted") }
        }

        btnDecline.setOnClickListener {
            appointmentId?.let { id -> updateAppointmentStatus(id, "Declined") }
        }

        // Set up forward button - FIXED IMPLEMENTATION
        btnForwardToLawyer.setOnClickListener {
            currentAppointment?.let { appointment ->
                forwardAppointmentToLawyer(appointment)
            }
        }
    }

    // Implement forwarding logic directly in this activity instead of using the fragment
    private fun forwardAppointmentToLawyer(appointment: Appointment) {
        val loadingSnackbar = Snackbar.make(
            findViewById(android.R.id.content),
            "Processing forwarding request...",
            Snackbar.LENGTH_INDEFINITE
        )
        loadingSnackbar.show()

        // 1. First, update the appointment status to "Forwarded"
        val appointmentRef = database.child("appointments").child(appointment.appointmentId)
        appointmentRef.child("status").setValue("Forwarded").addOnSuccessListener {

            // Update status in accepted_appointment node as well
            database.child("accepted_appointment").child(appointment.appointmentId)
                .child("status").setValue("Forwarded")

            // 2. Find the conversation between secretary and client
            findSecretaryClientConversation(appointment.clientId) { secretaryConversationId ->
                if (secretaryConversationId == null) {
                    loadingSnackbar.dismiss()
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "No active conversation found for this appointment",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@findSecretaryClientConversation
                }

                // 3. Create a new conversation between lawyer and client
                val lawyerId = appointment.lawyerId
                val clientId = appointment.clientId
                val newConversationId = ConversationUtils.generateConversationId(clientId, lawyerId)

                // 4. Extract problem description from original conversation
                ConversationUtils.extractProblemFromConversation(
                    database,
                    secretaryConversationId
                ) { problemDescription ->
                    // 5. Mark secretary conversation as forwarded
                    closeSecretaryClientConversation(
                        secretaryConversationId,
                        appointment,
                        problemDescription
                    ) {
                        // 6. Create lawyer-client conversation
                        createLawyerClientConversation(
                            appointment,
                            secretaryConversationId,
                            newConversationId,
                            problemDescription
                        ) {
                            // 7. Create notifications
                            createForwardingNotifications(
                                appointment,
                                secretaryConversationId,
                                newConversationId,
                                problemDescription
                            )

                            loadingSnackbar.dismiss()
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                "Appointment successfully forwarded to lawyer",
                                Snackbar.LENGTH_SHORT
                            ).show()

                            // Update UI to show forwarded status
                            tvStatus.text = "Status: Forwarded"
                            tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                            // Hide the forward button
                            cardViewForward.visibility = View.GONE
                        }
                    }
                }
            }
        }.addOnFailureListener { e ->
            loadingSnackbar.dismiss()
            Log.e("SecretaryAppointment", "Error updating appointment status: ${e.message}")
            Snackbar.make(
                findViewById(android.R.id.content),
                "Failed to forward appointment",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun findSecretaryClientConversation(clientId: String, callback: (String?) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return callback(null)

        // Generate the expected conversation ID
        val expectedConversationId = ConversationUtils.generateConversationId(currentUserId, clientId)

        // Check if conversation exists
        database.child("conversations").child(expectedConversationId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        callback(expectedConversationId)
                    } else {
                        // If not found by ID, search all conversations for this client
                        searchAllConversationsForClient(clientId, currentUserId, callback)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Error finding conversation: ${error.message}")
                    callback(null)
                }
            })
    }

    private fun searchAllConversationsForClient(
        clientId: String,
        secretaryId: String,
        callback: (String?) -> Unit
    ) {
        database.child("conversations")
            .orderByChild("participantIds/$secretaryId")
            .equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundConversationId: String? = null

                    for (conversationSnapshot in snapshot.children) {
                        // Check if client is a participant
                        if (conversationSnapshot.child("participantIds/$clientId").exists() &&
                            conversationSnapshot.child("participantIds/$clientId").getValue(Boolean::class.java) == true) {
                            foundConversationId = conversationSnapshot.key
                            break
                        }
                    }

                    callback(foundConversationId)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Error searching conversations: ${error.message}")
                    callback(null)
                }
            })
    }

    // Update the closeSecretaryClientConversation method in SecretaryAppointmentDetailsActivity

    private fun closeSecretaryClientConversation(
        conversationId: String,
        appointment: Appointment,
        problemDescription: String,
        callback: () -> Unit
    ) {
        val secretaryConversationRef = database.child("conversations").child(conversationId)

        // Get the lawyer's name first
        database.child("lawyers").child(appointment.lawyerId).get().addOnSuccessListener { lawyerSnapshot ->
            val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"

            // Update status flags
            val updates = mapOf(
                "forwarded" to true,
                "secretaryActive" to false,  // Set to false, but don't change participation
                "forwardedToLawyerId" to appointment.lawyerId,
                "forwardedAt" to ServerValue.TIMESTAMP,
                "appointmentId" to appointment.appointmentId
                // Remove the line that would change participantIds
            )

            secretaryConversationRef.updateChildren(updates).addOnSuccessListener {
                // Add a system message with lawyer name
                val systemMessage = mapOf(
                    "senderId" to "system",
                    "message" to "This conversation has been forwarded to Atty. $lawyerName. The client can now communicate directly with the lawyer.",
                    "timestamp" to ServerValue.TIMESTAMP
                )

                secretaryConversationRef.child("messages").push().setValue(systemMessage)
                    .addOnSuccessListener {
                        callback()
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryAppointment", "Error adding system message: ${e.message}")
                        callback() // Still proceed with the rest
                    }
            }.addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error closing secretary conversation: ${e.message}")
                callback() // Still proceed with the rest
            }
        }.addOnFailureListener { e ->
            // Fallback if we can't get the lawyer name
            Log.e("SecretaryAppointment", "Error getting lawyer name: ${e.message}")

            // Update status flags
            val updates = mapOf(
                "forwarded" to true,
                "secretaryActive" to false,
                "forwardedToLawyerId" to appointment.lawyerId,
                "forwardedAt" to ServerValue.TIMESTAMP,
                "appointmentId" to appointment.appointmentId
            )

            secretaryConversationRef.updateChildren(updates).addOnSuccessListener {
                // Add a generic system message without lawyer name
                val systemMessage = mapOf(
                    "senderId" to "system",
                    "message" to "This conversation has been forwarded to a lawyer. The client can now communicate directly with the lawyer.",
                    "timestamp" to ServerValue.TIMESTAMP
                )

                secretaryConversationRef.child("messages").push().setValue(systemMessage)
                    .addOnSuccessListener {
                        callback()
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryAppointment", "Error adding system message: ${e.message}")
                        callback() // Still proceed with the rest
                    }
            }.addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error closing secretary conversation: ${e.message}")
                callback() // Still proceed with the rest
            }
        }
    }

    private fun createLawyerClientConversation(
        appointment: Appointment,
        originalConversationId: String,
        newConversationId: String,
        problemDescription: String,
        callback: () -> Unit
    ) {
        val lawyerId = appointment.lawyerId
        val clientId = appointment.clientId
        val newConversationRef = database.child("conversations").child(newConversationId)

        // Check if conversation already exists
        newConversationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Update existing conversation
                    val updates = mapOf(
                        "originalConversationId" to originalConversationId,
                        "handledByLawyer" to true,
                        "forwardedFromSecretary" to true,
                        "participantIds/$lawyerId" to true,
                        "participantIds/$clientId" to true,
                        "appointmentId" to appointment.appointmentId,
                        "problem" to problemDescription,
                        "createdAt" to ServerValue.TIMESTAMP
                    )

                    newConversationRef.updateChildren(updates).addOnSuccessListener {
                        // Add a system message
                        addSystemAndWelcomeMessages(
                            newConversationRef,
                            lawyerId,
                            clientId,
                            appointment,
                            problemDescription,
                            callback
                        )
                    }.addOnFailureListener { e ->
                        Log.e("SecretaryAppointment", "Error updating lawyer conversation: ${e.message}")
                        callback()
                    }
                } else {
                    // Create new conversation
                    val conversationData = mapOf(
                        "participantIds" to mapOf(
                            lawyerId to true,
                            clientId to true
                        ),
                        "unreadMessages" to mapOf(
                            clientId to 1,  // Client has 1 unread message
                            lawyerId to 0   // Lawyer has 0 unread messages initially
                        ),
                        "appointedLawyerId" to lawyerId,
                        "originalConversationId" to originalConversationId,
                        "handledByLawyer" to true,
                        "forwardedFromSecretary" to true,
                        "createdAt" to ServerValue.TIMESTAMP,
                        "appointmentId" to appointment.appointmentId,
                        "problem" to problemDescription
                    )

                    newConversationRef.setValue(conversationData).addOnSuccessListener {
                        // Add a system message
                        addSystemAndWelcomeMessages(
                            newConversationRef,
                            lawyerId,
                            clientId,
                            appointment,
                            problemDescription,
                            callback
                        )
                    }.addOnFailureListener { e ->
                        Log.e("SecretaryAppointment", "Error creating lawyer conversation: ${e.message}")
                        callback()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAppointment", "Error checking lawyer conversation: ${error.message}")
                callback()
            }
        })
    }

    private fun addSystemAndWelcomeMessages(
        conversationRef: DatabaseReference,
        lawyerId: String,
        clientId: String,
        appointment: Appointment,
        problemDescription: String,
        callback: () -> Unit
    ) {
        // First get the secretary's name
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        database.child("secretaries").child(currentSecretaryId).get().addOnSuccessListener { secretarySnapshot ->
            val secretaryName = secretarySnapshot.child("name").getValue(String::class.java) ?: "a secretary"

            // Add system message with secretary name
            val systemMessage = mapOf(
                "senderId" to "system",
                "message" to "This is a forwarded conversation from secretary $secretaryName. Original problem: $problemDescription",
                "timestamp" to ServerValue.TIMESTAMP
            )

            conversationRef.child("messages").push().setValue(systemMessage).addOnSuccessListener {
                // Now fetch the lawyer name and add welcome message
                fetchLawyerNameAndAddWelcomeMessage(conversationRef, lawyerId, clientId, appointment, callback)
            }.addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error adding system message: ${e.message}")
                callback()
            }
        }.addOnFailureListener { e ->
            // If we can't get the secretary name, fall back to generic system message
            val systemMessage = mapOf(
                "senderId" to "system",
                "message" to "This is a forwarded conversation from a secretary. Original problem: $problemDescription",
                "timestamp" to ServerValue.TIMESTAMP
            )

            conversationRef.child("messages").push().setValue(systemMessage).addOnSuccessListener {
                // Continue with lawyer name fetch and welcome message
                fetchLawyerNameAndAddWelcomeMessage(conversationRef, lawyerId, clientId, appointment, callback)
            }.addOnFailureListener { err ->
                Log.e("SecretaryAppointment", "Error adding fallback system message: ${err.message}")
                callback()
            }
        }
    }

    private fun fetchLawyerNameAndAddWelcomeMessage(
        conversationRef: DatabaseReference,
        lawyerId: String,
        clientId: String,
        appointment: Appointment,
        callback: () -> Unit
    ) {
        // Get the lawyer's name for the welcome message
        database.child("lawyers").child(lawyerId).get().addOnSuccessListener { lawyerSnapshot ->
            val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"

            // Add welcome message with the actual lawyer name
            val welcomeMessage = mapOf(
                "message" to "Hello ${appointment.fullName}, I'm Atty. ${lawyerName}. I've been assigned to provide you with legal assistance.",
                "senderId" to lawyerId,
                "receiverId" to clientId,
                "timestamp" to (System.currentTimeMillis() + 1) // Ensure this comes after system message
            )

            conversationRef.child("messages").push().setValue(welcomeMessage).addOnSuccessListener {
                // Increment unread counter for client
                incrementUnreadCounter(conversationRef.key ?: "", clientId)
                callback()
            }.addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error adding welcome message: ${e.message}")
                callback()
            }
        }.addOnFailureListener { e ->
            // If we can't get the lawyer name, fall back to a generic welcome message
            val welcomeMessage = mapOf(
                "message" to "Hello ${appointment.fullName}, I've been assigned to provide you with legal assistance.",
                "senderId" to lawyerId,
                "receiverId" to clientId,
                "timestamp" to (System.currentTimeMillis() + 1) // Ensure this comes after system message
            )

            conversationRef.child("messages").push().setValue(welcomeMessage).addOnSuccessListener {
                incrementUnreadCounter(conversationRef.key ?: "", clientId)
                callback()
            }.addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error adding welcome message: ${e.message}")
                callback()
            }
        }
    }

    private fun incrementUnreadCounter(conversationId: String, userId: String) {
        val unreadRef = database.child("conversations").child(conversationId)
            .child("unreadMessages").child(userId)

        unreadRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                unreadRef.setValue(currentCount + 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAppointment", "Error updating unread count: ${error.message}")
            }
        })
    }

    private fun createForwardingNotifications(
        appointment: Appointment,
        originalConversationId: String,
        newConversationId: String,
        problemDescription: String
    ) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val clientId = appointment.clientId
        val lawyerId = appointment.lawyerId

        // Get secretary name
        database.child("secretaries").child(currentSecretaryId).get().addOnSuccessListener { secretarySnapshot ->
            val secretaryName = secretarySnapshot.child("name").getValue(String::class.java) ?: "Secretary"

            // 1. Create notification for client
            createClientForwardingNotification(
                clientId,
                appointment,
                secretaryName,
                newConversationId,
                originalConversationId
            )

            // 2. Create notification for lawyer
            createLawyerForwardingNotification(
                lawyerId,
                appointment,
                secretaryName,
                clientId,
                newConversationId,
                problemDescription
            )
        }.addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error fetching secretary name: ${e.message}")

            // Fall back to generic names
            createClientForwardingNotification(
                clientId,
                appointment,
                "a secretary",
                newConversationId,
                originalConversationId
            )

            createLawyerForwardingNotification(
                lawyerId,
                appointment,
                "a secretary",
                clientId,
                newConversationId,
                problemDescription
            )
        }
    }

    private fun createClientForwardingNotification(
        clientId: String,
        appointment: Appointment,
        secretaryName: String,
        newConversationId: String,
        originalConversationId: String
    ) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Create notification for client
        val notificationRef = database.child("notifications").child(clientId).push()
        val notificationId = notificationRef.key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to currentSecretaryId,
            "senderName" to "Secretary $secretaryName",
            "message" to "Your case has been forwarded to Atty. ${appointment.lawyerName} who will assist you further.",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "CONVERSATION_FORWARDED",
            "isRead" to false,
            "conversationId" to newConversationId,
            "originalConversationId" to originalConversationId,
            "appointmentId" to appointment.appointmentId
        )

        notificationRef.setValue(notificationData).addOnSuccessListener {
            // Update unread notification count
            updateClientUnreadNotificationCount(clientId)
        }.addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error creating client notification: ${e.message}")
        }
    }

    private fun createLawyerForwardingNotification(
        lawyerId: String,
        appointment: Appointment,
        secretaryName: String,
        clientId: String,
        newConversationId: String,
        problemDescription: String
    ) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Format date and time
        val currentDate = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        val currentTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())

        // Create formatted forwarding message
        val forwardingMessage = "The case been forwarded\n" +
                "from secretary $secretaryName regarding\n" +
                "appointment on $currentDate at $currentTime.\n" +
                "Original problem: $problemDescription"

        // Create notification for lawyer
        val notificationRef = database.child("notifications").child(lawyerId).push()
        val notificationId = notificationRef.key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to currentSecretaryId,
            "senderName" to "Secretary $secretaryName",
            "message" to "A new case from ${appointment.fullName} has been forwarded to you.",
            "forwardingMessage" to forwardingMessage,
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "NEW_CASE_ASSIGNED",
            "isRead" to false,
            "conversationId" to newConversationId,
            "clientId" to clientId,
            "appointmentId" to appointment.appointmentId
        )

        notificationRef.setValue(notificationData).addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error creating lawyer notification: ${e.message}")
        }
    }

    private fun updateClientUnreadNotificationCount(clientId: String) {
        // Query to count unread notifications
        database.child("notifications").child(clientId)
            .orderByChild("isRead").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val unreadCount = snapshot.childrenCount.toInt()

                    // Update the client's unread notification count
                    database.child("Users").child(clientId)
                        .child("unreadNotifications").setValue(unreadCount)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Failed to count unread notifications: ${error.message}")
                }
            })
    }

    private fun fetchCurrentSecretaryName() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries").child(currentUserId)

        secretaryRef.child("name").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentSecretaryName = snapshot.getValue(String::class.java) ?: "Secretary"
                tvSecretaryName.text = "Secretary: $currentSecretaryName"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAppointment", "Error fetching secretary name: ${error.message}")
            }
        })
    }

    private fun loadAppointmentDetails(appointmentId: String) {
        showLoading(true)

        val appointmentRef = FirebaseDatabase.getInstance().getReference("appointments")
            .child(appointmentId)

        appointmentRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                showLoading(false)

                if (snapshot.exists()) {
                    val appointment = snapshot.getValue(Appointment::class.java)
                    appointment?.let {
                        it.appointmentId = appointmentId
                        currentAppointment = it
                        displayAppointmentDetails(it)

                        // Show or hide action buttons based on status
                        when (it.status?.lowercase()) {
                            "pending" -> {
                                cardViewActions.visibility = View.VISIBLE
                                cardViewForward.visibility = View.GONE
                            }
                            "accepted" -> {
                                cardViewActions.visibility = View.GONE
                                cardViewForward.visibility = View.VISIBLE
                            }
                            "forwarded" -> {
                                cardViewActions.visibility = View.GONE
                                cardViewForward.visibility = View.GONE
                            }
                            else -> {
                                cardViewActions.visibility = View.GONE
                                cardViewForward.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    Toast.makeText(
                        this@SecretaryAppointmentDetailsActivity,
                        "Appointment not found",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                Log.e("SecretaryAppointment", "Error loading appointment: ${error.message}")
                Toast.makeText(
                    this@SecretaryAppointmentDetailsActivity,
                    "Error loading appointment details",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun displayAppointmentDetails(appointment: Appointment) {
        tvAppointmentTitle.text = "Appointment with ${appointment.fullName}"
        tvStatus.text = "Status: ${appointment.status}"
        tvAppointmentDate.text = "Date: ${appointment.date}"
        tvAppointmentTime.text = "Time: ${appointment.time}"
        tvProblemDescription.text = appointment.problem

        // Set status color based on status
        when (appointment.status?.lowercase()) {
            "pending" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
            "accepted" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            "declined" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            "forwarded" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
            else -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
        }

        // Fetch lawyer name
        fetchLawyerName(appointment.lawyerId)

        // Set secretary name
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUserId.isNotEmpty()) {
            tvSecretaryName.text = "Secretary: $currentSecretaryName"
        } else {
            tvSecretaryName.text = "Secretary: Not Assigned"
        }
    }

    private fun fetchLawyerName(lawyerId: String) {
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)
        lawyerRef.child("name").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lawyerName = snapshot.getValue(String::class.java) ?: "Unknown Lawyer"
                tvLawyerName.text = "Lawyer: $lawyerName"
            }

            override fun onCancelled(error: DatabaseError) {
                tvLawyerName.text = "Lawyer: Not Available"
                Log.e("SecretaryAppointment", "Error fetching lawyer name: ${error.message}")
            }
        })
    }

    private fun updateAppointmentStatus(appointmentId: String, newStatus: String) {
        showLoading(true)

        val statusRef = FirebaseDatabase.getInstance().getReference("appointments")
            .child(appointmentId).child("status")

        statusRef.setValue(newStatus)
            .addOnSuccessListener {
                if (newStatus == "Accepted") {
                    // Add appointment to accepted_appointment node
                    addToAcceptedAppointments(appointmentId)
                } else {
                    // Update UI for declined appointment
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Appointment has been $newStatus",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Update UI
                    tvStatus.text = "Status: $newStatus"
                    tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))

                    // Hide action buttons after status update
                    cardViewActions.visibility = View.GONE
                    cardViewForward.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SecretaryAppointment", "Error updating status: ${e.message}")
                Toast.makeText(
                    this,
                    "Failed to update appointment status",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun addToAcceptedAppointments(appointmentId: String) {
        val database = FirebaseDatabase.getInstance()
        val appointmentsRef = database.getReference("appointments").child(appointmentId)
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val appointment = snapshot.getValue(Appointment::class.java)
                    appointment?.let {
                        // Set appointment ID and secretary ID
                        it.appointmentId = appointmentId
                        it.secretaryId = currentSecretaryId

                        // Store the current appointment
                        currentAppointment = it

                        // Add to accepted_appointment node
                        val acceptedAppointmentsRef = database.getReference("accepted_appointment").child(appointmentId)
                        acceptedAppointmentsRef.setValue(it)
                            .addOnSuccessListener {
                                // Also add to lawyer's appointments
                                val lawyerAppointmentsRef = database.getReference("lawyers")
                                    .child(appointment.lawyerId)
                                    .child("appointments")
                                    .child(appointmentId)

                                lawyerAppointmentsRef.setValue(appointment)
                                    .addOnSuccessListener {
                                        // Add to secretary's appointments
                                        val secretaryAppointmentsRef = database.getReference("secretaries")
                                            .child(currentSecretaryId)
                                            .child("appointments")
                                            .child(appointmentId)

                                        secretaryAppointmentsRef.setValue(appointment)
                                            .addOnSuccessListener {
                                                // Send notifications to client and lawyer
                                                sendNotificationToClient(appointment.clientId, currentSecretaryId, appointment)
                                                sendNotificationToLawyer(appointment.lawyerId, currentSecretaryId, appointment)

                                                showLoading(false)

                                                Toast.makeText(
                                                    this@SecretaryAppointmentDetailsActivity,
                                                    "Appointment has been Accepted",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // Update UI
                                                tvStatus.text = "Status: Accepted"
                                                tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))

                                                // Hide action buttons and show forward option
                                                cardViewActions.visibility = View.GONE
                                                cardViewForward.visibility = View.VISIBLE
                                            }
                                            .addOnFailureListener { e ->
                                                showLoading(false)
                                                Log.e("SecretaryAppointment", "Error adding to secretary appointments: ${e.message}")
                                                Toast.makeText(
                                                    this@SecretaryAppointmentDetailsActivity,
                                                    "Error finalizing appointment acceptance",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        showLoading(false)
                                        Log.e("SecretaryAppointment", "Error adding to lawyer appointments: ${e.message}")
                                        Toast.makeText(
                                            this@SecretaryAppointmentDetailsActivity,
                                            "Error adding appointment to lawyer",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                showLoading(false)
                                Log.e("SecretaryAppointment", "Error adding to accepted appointments: ${e.message}")
                                Toast.makeText(
                                    this@SecretaryAppointmentDetailsActivity,
                                    "Failed to add to accepted appointments",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                } else {
                    showLoading(false)
                    Toast.makeText(
                        this@SecretaryAppointmentDetailsActivity,
                        "Appointment not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                Log.e("SecretaryAppointment", "Error retrieving appointment: ${error.message}")
                Toast.makeText(
                    this@SecretaryAppointmentDetailsActivity,
                    "Error retrieving appointment details",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // Send notification to client
    private fun sendNotificationToClient(clientId: String, secretaryId: String, appointment: Appointment) {
        val database = FirebaseDatabase.getInstance().reference
        val notificationId = database.child("notifications").child(clientId).push().key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to secretaryId,
            "senderName" to currentSecretaryName,
            "message" to "Your appointment on ${appointment.date} at ${appointment.time} has been accepted",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "appointment_accepted",
            "isRead" to false,
            "appointmentId" to appointment.appointmentId
        )

        database.child("notifications").child(clientId).child(notificationId)
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d("Notification", "Notification sent to client successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to send notification to client: ${e.message}")
            }
    }

    // Send notification to lawyer
    private fun sendNotificationToLawyer(lawyerId: String, secretaryId: String, appointment: Appointment) {
        val database = FirebaseDatabase.getInstance().reference
        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to secretaryId,
            "senderName" to currentSecretaryName,
            "message" to "You have an appointment with ${appointment.fullName} on ${appointment.date} at ${appointment.time}.",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "appointment_accepted",
            "isRead" to false,
            "appointmentId" to appointment.appointmentId
        )

        database.child("notifications").child(lawyerId).child(notificationId)
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d("Notification", "Notification sent to lawyer successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to send notification to lawyer: ${e.message}")
            }
    }
}