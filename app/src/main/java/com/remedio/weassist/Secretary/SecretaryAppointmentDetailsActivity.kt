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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
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
    private lateinit var cardViewActions: CardView

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

        // Set up back button
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Get appointment ID from intent
        val appointmentId = intent.getStringExtra("APPOINTMENT_ID")
        if (appointmentId != null) {
            loadAppointmentDetails(appointmentId)
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
                        displayAppointmentDetails(it)

                        // Show or hide action buttons based on status
                        if (it.status == "Pending") {
                            cardViewActions.visibility = View.VISIBLE
                        } else {
                            cardViewActions.visibility = View.GONE
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
            else -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
        }

        // Fetch lawyer name
        fetchLawyerName(appointment.lawyerId)

        // For demonstration, use a placeholder for secretary name
        // In a real app, you would fetch this from the database
        tvSecretaryName.text = "Secretary: Current Secretary"
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
                showLoading(false)
                Toast.makeText(
                    this,
                    "Appointment has been $newStatus",
                    Toast.LENGTH_SHORT
                ).show()

                // Update UI
                tvStatus.text = "Status: $newStatus"
                when (newStatus) {
                    "Accepted" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    "Declined" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                }

                // Hide action buttons after status update
                cardViewActions.visibility = View.GONE
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

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}