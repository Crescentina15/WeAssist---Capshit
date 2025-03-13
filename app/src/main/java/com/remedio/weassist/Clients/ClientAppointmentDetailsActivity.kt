package com.remedio.weassist.Clients

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.R

class ClientAppointmentDetailsActivity : AppCompatActivity() {

    private lateinit var tvAppointmentTitle: TextView
    private lateinit var tvAppointmentDate: TextView
    private lateinit var tvAppointmentTime: TextView
    private lateinit var tvLawyerName: TextView
    private lateinit var tvSecretaryName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvProblem: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton
    private lateinit var btnReschedule: Button
    private lateinit var btnCancel: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var appointmentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_appointment_details)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // Get appointment ID from intent
        appointmentId = intent.getStringExtra("APPOINTMENT_ID") ?: ""
        if (appointmentId.isEmpty()) {
            Toast.makeText(this, "Appointment not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UI components
        initializeViews()

        // Set up back button
        btnBack.setOnClickListener {
            finish()
        }

        // Load appointment details
        loadAppointmentDetails()
    }

    private fun initializeViews() {
        tvAppointmentTitle = findViewById(R.id.tvAppointmentTitle)
        tvAppointmentDate = findViewById(R.id.tvAppointmentDate)
        tvAppointmentTime = findViewById(R.id.tvAppointmentTime)
        tvLawyerName = findViewById(R.id.tvLawyerName)
        tvSecretaryName = findViewById(R.id.tvSecretaryName)
        tvStatus = findViewById(R.id.tvStatus)
        tvProblem = findViewById(R.id.tvProblemDescription)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)
        btnReschedule = findViewById(R.id.btnReschedule)
        btnCancel = findViewById(R.id.btnCancel)

        // Set up buttons
        btnReschedule.setOnClickListener {
            // Implement reschedule functionality
            Toast.makeText(this, "Reschedule functionality coming soon", Toast.LENGTH_SHORT).show()
        }

        btnCancel.setOnClickListener {
            cancelAppointment()
        }
    }

    private fun loadAppointmentDetails() {
        progressBar.visibility = View.VISIBLE

        // Get the current user ID
        val userId = auth.currentUser?.uid ?: return

        // Reference to the appointment in the user's appointments
        val appointmentRef = FirebaseDatabase.getInstance().reference
            .child("users")
            .child(userId)
            .child("appointments")
            .child(appointmentId)

        appointmentRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE

                if (snapshot.exists()) {
                    // Parse appointment data
                    val appointment = snapshot.getValue(Appointment::class.java)
                    appointment?.let { displayAppointmentDetails(it) }
                } else {
                    // If not found in user's appointments, try accepted_appointment collection
                    val acceptedAppointmentRef = FirebaseDatabase.getInstance().reference
                        .child("accepted_appointment")
                        .child(appointmentId)

                    acceptedAppointmentRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(acceptedSnapshot: DataSnapshot) {
                            if (acceptedSnapshot.exists()) {
                                val appointment = acceptedSnapshot.getValue(Appointment::class.java)
                                appointment?.let { displayAppointmentDetails(it) }
                            } else {
                                Toast.makeText(this@ClientAppointmentDetailsActivity,
                                    "Appointment not found", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@ClientAppointmentDetailsActivity,
                                "Error loading appointment: ${error.message}", Toast.LENGTH_SHORT).show()
                            Log.e("AppointmentDetails", "Database error: ${error.message}")
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ClientAppointmentDetailsActivity,
                    "Error loading appointment: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("AppointmentDetails", "Database error: ${error.message}")
            }
        })
    }

    private fun displayAppointmentDetails(appointment: Appointment) {
        // Set appointment details in UI
        tvAppointmentTitle.text = "Appointment Details"
        tvAppointmentDate.text = "Date: ${appointment.date ?: "N/A"}"
        tvAppointmentTime.text = "Time: ${appointment.time ?: "N/A"}"
        tvProblem.text = appointment.problem ?: "No problem description provided"
        tvStatus.text = "Status: ${appointment.status ?: "Pending"}"

        // Load lawyer name
        appointment.lawyerId?.let { lawyerId ->
            val lawyerRef = FirebaseDatabase.getInstance().reference.child("lawyers").child(lawyerId)
            lawyerRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                        tvLawyerName.text = "Lawyer: $firstName $lastName"
                    } else {
                        tvLawyerName.text = "Lawyer: Not available"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    tvLawyerName.text = "Lawyer: Not available"
                }
            })
        } ?: run {
            tvLawyerName.text = "Lawyer: Not assigned"
        }

        // Load secretary name if available
        appointment.secretaryId?.let { secretaryId ->
            val secretaryRef = FirebaseDatabase.getInstance().reference.child("secretaries").child(secretaryId)
            secretaryRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        tvSecretaryName.text = "Secretary: $name"
                    } else {
                        tvSecretaryName.text = "Secretary: Not available"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    tvSecretaryName.text = "Secretary: Not available"
                }
            })
        } ?: run {
            tvSecretaryName.text = "Secretary: Not assigned"
        }

        // Enable or disable reschedule/cancel buttons based on appointment status
        val canModify = appointment.status == "Pending" || appointment.status == "Accepted"
        btnReschedule.isEnabled = canModify
        btnCancel.isEnabled = canModify
    }

    private fun cancelAppointment() {
        // Show confirmation dialog
        android.app.AlertDialog.Builder(this)
            .setTitle("Cancel Appointment")
            .setMessage("Are you sure you want to cancel this appointment?")
            .setPositiveButton("Yes") { _, _ ->
                performCancellation()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performCancellation() {
        progressBar.visibility = View.VISIBLE

        val userId = auth.currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance().reference

        // Update status to "Cancelled" in user's appointments
        database.child("users").child(userId).child("appointments").child(appointmentId)
            .child("status").setValue("Cancelled")
            .addOnSuccessListener {
                // Update in accepted_appointment if it exists there
                database.child("accepted_appointment").child(appointmentId)
                    .child("status").setValue("Cancelled")
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Appointment cancelled successfully", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Status: Cancelled"
                        btnReschedule.isEnabled = false
                        btnCancel.isEnabled = false

                        // Check if the appointment has a lawyer and secretary assigned
                        database.child("accepted_appointment").child(appointmentId).get()
                            .addOnSuccessListener { snapshot ->
                                val appointment = snapshot.getValue(Appointment::class.java)

                                // Notify lawyer if assigned
                                appointment?.lawyerId?.let { lawyerId ->
                                    database.child("lawyers").child(lawyerId).child("appointments")
                                        .child(appointmentId).child("status").setValue("Cancelled")
                                }

                                // Notify secretary if assigned
                                appointment?.secretaryId?.let { secretaryId ->
                                    database.child("secretaries").child(secretaryId).child("appointments")
                                        .child(appointmentId).child("status").setValue("Cancelled")

                                    // Create a notification for the secretary
                                    val notificationId = database.child("notifications").child(secretaryId).push().key
                                    if (notificationId != null) {
                                        val notification = mapOf(
                                            "senderId" to userId,
                                            "message" to "Appointment on ${appointment.date} at ${appointment.time} has been cancelled by the client",
                                            "timestamp" to System.currentTimeMillis(),
                                            "type" to "appointment_cancelled",
                                            "isRead" to false,
                                            "appointmentId" to appointmentId
                                        )

                                        database.child("notifications").child(secretaryId).child(notificationId)
                                            .setValue(notification)
                                    }
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Log.e("AppointmentDetails", "Error updating accepted_appointment: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to cancel appointment: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("AppointmentDetails", "Error cancelling appointment: ${e.message}")
            }
    }
}