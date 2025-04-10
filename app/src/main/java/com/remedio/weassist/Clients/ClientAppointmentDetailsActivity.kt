package com.remedio.weassist.Clients

import android.os.Bundle
import android.util.Log
import android.view.View
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

        // Display intent extras if available while loading full details
        intent.getStringExtra("LAWYER_NAME")?.let { name ->
            tvLawyerName.text = "Lawyer: $name"
        }
        intent.getStringExtra("DATE")?.let { date ->
            tvAppointmentDate.text = "Date: $date"
        }
        intent.getStringExtra("TIME")?.let { time ->
            tvAppointmentTime.text = "Time: $time"
        }
        intent.getStringExtra("PROBLEM")?.let { problem ->
            tvProblem.text = problem
        }
        intent.getStringExtra("STATUS")?.let { status ->
            tvStatus.text = "Status: $status"
        }

        // Load complete appointment details
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
                if (snapshot.exists()) {
                    progressBar.visibility = View.GONE
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
                            progressBar.visibility = View.GONE
                            if (acceptedSnapshot.exists()) {
                                val appointment = acceptedSnapshot.getValue(Appointment::class.java)
                                appointment?.let { displayAppointmentDetails(it) }
                            } else {
                                Log.w("AppointmentDetails", "Appointment not found in either location. Using intent data only.")
                                // Don't finish the activity - we can still show the intent data
                                // Just hide the progress bar and use whatever data we have from intent
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@ClientAppointmentDetailsActivity,
                                "Error loading appointment details: ${error.message}", Toast.LENGTH_SHORT).show()
                            Log.e("AppointmentDetails", "Database error: ${error.message}")
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ClientAppointmentDetailsActivity,
                    "Error loading appointment details: ${error.message}", Toast.LENGTH_SHORT).show()
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
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        tvLawyerName.text = "Lawyer: $name"
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
    }
}