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

        // Update appointment status to "Forwarded"
        val database = FirebaseDatabase.getInstance()
        val appointmentsRef = database.getReference("appointments").child(appointment.appointmentId)
        appointmentsRef.child("status").setValue("Forwarded")
            .addOnSuccessListener {
                // Update status in accepted_appointment node as well
                database.getReference("accepted_appointment")
                    .child(appointment.appointmentId)
                    .child("status").setValue("Forwarded")

                // Send priority notification to lawyer
                sendPriorityNotificationToLawyer(appointment.lawyerId, appointment)

                // Update UI
                tvStatus.text = "Status: Forwarded"
                tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))

                // Hide forward option
                cardViewForward.visibility = View.GONE

                showLoading(false)
                Toast.makeText(
                    this@SecretaryAppointmentDetailsActivity,
                    "Appointment has been forwarded to lawyer",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SecretaryAppointment", "Error forwarding appointment: ${e.message}")
                Toast.makeText(
                    this@SecretaryAppointmentDetailsActivity,
                    "Failed to forward appointment",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun sendPriorityNotificationToLawyer(lawyerId: String, appointment: Appointment) {
        val database = FirebaseDatabase.getInstance().reference
        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to FirebaseAuth.getInstance().currentUser?.uid,
            "senderName" to currentSecretaryName,
            "message" to "PRIORITY: Appointment with ${appointment.fullName} on ${appointment.date} at ${appointment.time} has been forwarded to you.",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "appointment_forwarded",
            "isRead" to false,
            "priority" to true,
            "appointmentId" to appointment.appointmentId
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