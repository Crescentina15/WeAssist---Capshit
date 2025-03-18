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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.R

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
    private var currentSecretaryName: String = "Secretary"
    private var currentAppointment: Appointment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_appointment_details)

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

        // Set up forward button
        btnForwardToLawyer.setOnClickListener {
            currentAppointment?.let { appointment ->
                forwardAppointmentToLawyer(appointment)
            }
        }
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
                        when (it.status) {
                            "Pending" -> {
                                cardViewActions.visibility = View.VISIBLE
                                cardViewForward.visibility = View.GONE
                            }
                            "Accepted" -> {
                                cardViewActions.visibility = View.GONE
                                cardViewForward.visibility = View.VISIBLE
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
        when (appointment.status) {
            "Pending" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
            "Accepted" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            "Declined" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            "Forwarded" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
            else -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
        }

        // Fetch lawyer name
        fetchLawyerName(appointment.lawyerId)

        // For demonstration, use a placeholder for secretary name
        // In a real app, you would fetch this from the database
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

    private fun forwardAppointmentToLawyer(appointment: Appointment) {
        showLoading(true)

        // Get database references
        val database = FirebaseDatabase.getInstance()
        val conversationsRef = database.getReference("conversations")

        // Original conversation ID between secretary and client
        val secretaryClientConvId = "${appointment.secretaryId}_${appointment.clientId}"

        // Retrieve conversation between secretary and client
        conversationsRef.child(secretaryClientConvId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Transfer conversation ownership to lawyer
                        transferConversationToLawyer(
                            secretaryClientConvId,
                            appointment,
                            snapshot
                        )
                    } else {
                        // If no conversation exists, create a new one
                        createNewLawyerClientConversation(appointment)
                    }

                    // Update appointment status to "Forwarded"
                    updateAppointmentStatus(appointment)
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)
                    Log.e("SecretaryAppointment", "Error retrieving conversation: ${error.message}")
                    Toast.makeText(
                        this@SecretaryAppointmentDetailsActivity,
                        "Failed to retrieve conversation",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun updateAppointmentStatus(appointment: Appointment) {
        val database = FirebaseDatabase.getInstance()

        // Update status in the main appointments node
        database.getReference("appointments")
            .child(appointment.appointmentId)
            .child("status")
            .setValue("Forwarded")

        // Update status in accepted_appointment node
        database.getReference("accepted_appointment")
            .child(appointment.appointmentId)
            .child("status")
            .setValue("Forwarded")

        // Send priority notification to lawyer
        sendPriorityNotificationToLawyer(
            appointment.lawyerId,
            appointment,
            1
        )
    }

    private fun createNewLawyerClientConversation(appointment: Appointment) {
        val database = FirebaseDatabase.getInstance()
        val conversationsRef = database.getReference("conversations")
        val lawyerClientConvId = "${appointment.lawyerId}_${appointment.clientId}"

        // Get lawyer name
        database.getReference("lawyers").child(appointment.lawyerId).child("name")
            .get()
            .addOnSuccessListener { lawyerSnap ->
                val lawyerName = lawyerSnap.getValue(String::class.java) ?: "Lawyer"

                // Setup participant IDs
                val participantIds = mapOf(
                    appointment.lawyerId to true,
                    appointment.clientId to true
                )

                // Create initial message
                val initialMessage = mapOf(
                    "senderId" to appointment.secretaryId,
                    "senderName" to currentSecretaryName,
                    "message" to "This appointment has been forwarded to $lawyerName who will now handle your case. Original problem: ${appointment.problem}",
                    "timestamp" to ServerValue.TIMESTAMP
                )

                // Create conversation data
                val conversationData = mapOf(
                    "participantIds" to participantIds,
                    "lawyerId" to appointment.lawyerId,
                    "clientId" to appointment.clientId,
                    "appointmentId" to appointment.appointmentId,
                    "unreadMessages" to mapOf(
                        appointment.lawyerId to 1,
                        appointment.clientId to 0
                    )
                )

                // Save conversation
                conversationsRef.child(lawyerClientConvId).setValue(conversationData)
                    .addOnSuccessListener {
                        // Add initial message
                        conversationsRef.child(lawyerClientConvId)
                            .child("messages")
                            .push()
                            .setValue(initialMessage)

                        Log.d("SecretaryAppointment", "New lawyer-client conversation created")

                        // Update UI
                        tvStatus.text = "Status: Forwarded"
                        tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                        cardViewForward.visibility = View.GONE

                        showLoading(false)
                        Toast.makeText(
                            this@SecretaryAppointmentDetailsActivity,
                            "Appointment forwarded to lawyer successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Log.e("SecretaryAppointment", "Error creating lawyer conversation: ${e.message}")
                        Toast.makeText(
                            this@SecretaryAppointmentDetailsActivity,
                            "Failed to forward to lawyer",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SecretaryAppointment", "Error getting lawyer name: ${e.message}")
            }
    }

    private fun transferConversationToLawyer(
        secretaryClientConvId: String,
        appointment: Appointment,
        conversationSnapshot: DataSnapshot
    ) {
        val database = FirebaseDatabase.getInstance()
        val conversationsRef = database.getReference("conversations")

        // Create a new conversation ID for lawyer and client
        val lawyerClientConvId = "${appointment.lawyerId}_${appointment.clientId}"

        // Get all messages from the original conversation
        val messages = mutableListOf<Map<String, Any>>()
        conversationSnapshot.child("messages").children.forEach { messageSnap ->
            messageSnap.getValue(Map::class.java)?.let {
                messages.add(it as Map<String, Any>)
            }
        }

        // Get lawyer name
        database.getReference("lawyers").child(appointment.lawyerId).child("name")
            .get()
            .addOnSuccessListener { lawyerSnap ->
                val lawyerName = lawyerSnap.getValue(String::class.java) ?: "Lawyer"

                // Add transfer notification message
                val transferMessage = mapOf(
                    "senderId" to appointment.secretaryId,
                    "senderName" to currentSecretaryName,
                    "message" to "This conversation has been transferred to $lawyerName who will now handle your case.",
                    "timestamp" to ServerValue.TIMESTAMP
                )
                messages.add(transferMessage)

                // Setup new participant IDs for the lawyer-client conversation
                val participantIds = mapOf(
                    appointment.lawyerId to true,
                    appointment.clientId to true
                )

                // Create/Update the lawyer-client conversation
                val conversationData = mapOf(
                    "participantIds" to participantIds,
                    "lawyerId" to appointment.lawyerId,
                    "clientId" to appointment.clientId,
                    "appointmentId" to appointment.appointmentId,
                    "unreadMessages" to mapOf(
                        appointment.lawyerId to 1,
                        appointment.clientId to 0
                    )
                )

                // Save the conversation data
                conversationsRef.child(lawyerClientConvId).updateChildren(conversationData)
                    .addOnSuccessListener {
                        // Add all messages including the transfer notification
                        val messagesRef = conversationsRef.child(lawyerClientConvId).child("messages")
                        messagesRef.removeValue() // Clear any existing messages
                            .addOnSuccessListener {
                                // Add all messages in order
                                var counter = 0
                                for (message in messages) {
                                    messagesRef.push().setValue(message)
                                        .addOnSuccessListener {
                                            counter++
                                            if (counter == messages.size) {
                                                // All messages transferred, now archive the secretary conversation
                                                archiveSecretaryConversation(secretaryClientConvId, appointment)
                                            }
                                        }
                                }

                                Log.d("SecretaryAppointment", "Conversation transferred to lawyer successfully")

                                // Update UI
                                tvStatus.text = "Status: Forwarded"
                                tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                                cardViewForward.visibility = View.GONE

                                showLoading(false)
                                Toast.makeText(
                                    this@SecretaryAppointmentDetailsActivity,
                                    "Conversation transferred to lawyer successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Log.e("SecretaryAppointment", "Error transferring conversation: ${e.message}")
                        Toast.makeText(
                            this@SecretaryAppointmentDetailsActivity,
                            "Failed to transfer conversation",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SecretaryAppointment", "Error getting lawyer name: ${e.message}")
            }
    }

    private fun archiveSecretaryConversation(conversationId: String, appointment: Appointment) {
        val database = FirebaseDatabase.getInstance()
        val conversationsRef = database.getReference("conversations")

        // Mark the secretary conversation as archived by removing participation
        val updates = mapOf(
            "participantIds/${appointment.secretaryId}" to false,
            "archived" to true
        )

        conversationsRef.child(conversationId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("SecretaryAppointment", "Secretary conversation archived")
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error archiving secretary conversation: ${e.message}")
            }
    }

    private fun createLawyerClientConversation(appointment: Appointment, conversationHistory: List<Map<String, Any>>) {
        val database = FirebaseDatabase.getInstance()
        val conversationsRef = database.getReference("conversations")

        // Create a conversation ID for lawyer and client
        val conversationId = "${appointment.lawyerId}_${appointment.clientId}"

        // Check if conversation already exists
        conversationsRef.child(conversationId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Create new conversation
                    val participantIds = mapOf(
                        appointment.lawyerId to true,
                        appointment.clientId to true
                    )

                    // Get lawyer and client names
                    fetchParticipantNames(appointment.lawyerId, appointment.clientId) { lawyerName, clientName ->
                        // Create initial message about forwarded appointment
                        val initialMessage = mapOf(
                            "senderId" to appointment.secretaryId,
                            "senderName" to currentSecretaryName,
                            "message" to "This conversation has been forwarded from secretary $currentSecretaryName regarding appointment on ${appointment.date} at ${appointment.time}. Original problem: ${appointment.problem}",
                            "timestamp" to ServerValue.TIMESTAMP
                        )

                        // Create conversation data
                        val conversationData = mapOf(
                            "participantIds" to participantIds,
                            "createdAt" to ServerValue.TIMESTAMP,
                            "lawyerId" to appointment.lawyerId,
                            "clientId" to appointment.clientId,
                            "lawyerName" to lawyerName,
                            "clientName" to clientName,
                            "appointmentId" to appointment.appointmentId,
                            "unreadMessages" to mapOf(
                                appointment.lawyerId to 1,
                                appointment.clientId to 0
                            )
                        )

                        // Save conversation
                        conversationsRef.child(conversationId).setValue(conversationData)
                            .addOnSuccessListener {
                                // Add initial message
                                conversationsRef.child(conversationId)
                                    .child("messages")
                                    .push()
                                    .setValue(initialMessage)

                                // Add previous conversation history if available
                                if (conversationHistory.isNotEmpty()) {
                                    val messagesRef = conversationsRef.child(conversationId).child("messages")
                                    for (message in conversationHistory) {
                                        messagesRef.push().setValue(message)
                                    }
                                }

                                Log.d("SecretaryAppointment", "Lawyer-client conversation created successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e("SecretaryAppointment", "Failed to create lawyer-client conversation: ${e.message}")
                            }
                    }
                } else {
                    // Conversation already exists, just add a new message about forwarding
                    val forwardMessage = mapOf(
                        "senderId" to appointment.secretaryId,
                        "senderName" to currentSecretaryName,
                        "message" to "This appointment has been forwarded to the lawyer. Original problem: ${appointment.problem}",
                        "timestamp" to ServerValue.TIMESTAMP
                    )

                    conversationsRef.child(conversationId)
                        .child("messages")
                        .push()
                        .setValue(forwardMessage)

                    // Increment unread count for lawyer
                    conversationsRef.child(conversationId)
                        .child("unreadMessages")
                        .child(appointment.lawyerId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                                conversationsRef.child(conversationId)
                                    .child("unreadMessages")
                                    .child(appointment.lawyerId)
                                    .setValue(currentCount + 1)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("SecretaryAppointment", "Error updating unread count: ${error.message}")
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAppointment", "Error checking existing conversation: ${error.message}")
            }
        })
    }

    private fun fetchParticipantNames(lawyerId: String, clientId: String, callback: (String, String) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        var lawyerName = "Lawyer"
        var clientName = "Client"
        var completedFetches = 0

        // Fetch lawyer name
        database.getReference("lawyers").child(lawyerId).child("name")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    lawyerName = snapshot.getValue(String::class.java) ?: "Lawyer"
                    completedFetches++
                    if (completedFetches == 2) callback(lawyerName, clientName)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Error fetching lawyer name: ${error.message}")
                    completedFetches++
                    if (completedFetches == 2) callback(lawyerName, clientName)
                }
            })

        // Fetch client name
        database.getReference("Users").child(clientId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                    val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                    clientName = "$firstName $lastName".trim()
                    if (clientName.isEmpty()) clientName = "Client"

                    completedFetches++
                    if (completedFetches == 2) callback(lawyerName, clientName)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Error fetching client name: ${error.message}")
                    completedFetches++
                    if (completedFetches == 2) callback(lawyerName, clientName)
                }
            })
    }


    private fun sendPriorityNotificationToLawyer(lawyerId: String, appointment: Appointment,
                                                 conversationCount: Int = 0) {
        val database = FirebaseDatabase.getInstance().reference
        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return

        val conversationInfo = if (conversationCount > 0) {
            " ($conversationCount messages in conversation history included)"
        } else {
            ""
        }

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to FirebaseAuth.getInstance().currentUser?.uid,
            "senderName" to currentSecretaryName,
            "message" to "PRIORITY: Appointment with ${appointment.fullName} on ${appointment.date} at ${appointment.time} has been forwarded to you.$conversationInfo",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "appointment_forwarded",
            "isRead" to false,
            "priority" to true,
            "appointmentId" to appointment.appointmentId,
            "hasConversation" to (conversationCount > 0)
        )

        database.child("notifications").child(lawyerId).child(notificationId)
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d("Notification", "Priority notification sent to lawyer successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to send priority notification to lawyer: ${e.message}")
            }
    }

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

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}