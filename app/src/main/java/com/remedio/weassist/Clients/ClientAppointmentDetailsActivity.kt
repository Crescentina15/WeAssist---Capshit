package com.remedio.weassist.Clients

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.Consultation
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

    // UI elements for consultation logs
    private lateinit var cardViewConsultationLogs: CardView
    private lateinit var tvConsultationLogsTitle: TextView
    private lateinit var tvConsultationLogsContent: TextView
    private lateinit var tvNoConsultationLogs: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var appointmentId: String
    private var foundClientId: String? = null
    private var lawyerId: String? = null
    private var clientName: String? = null

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

        // Immediately display intent extras while waiting for full data
        displayIntentData()

        // Show loading indicator
        progressBar.visibility = View.VISIBLE

        // Initially show loading for consultation logs
        tvNoConsultationLogs.visibility = View.VISIBLE
        tvNoConsultationLogs.text = "Loading consultation logs..."

        // Load complete appointment details
        loadAppointmentDetails()

        // In the onCreate method:
        appointmentId = intent.getStringExtra("APPOINTMENT_ID") ?: ""
        if (appointmentId.isEmpty()) {
            Log.e("ClientAppointmentDetails", "No appointment ID provided")
        }
    }

    private fun displayIntentData() {
        intent.getStringExtra("LAWYER_NAME")?.let { name ->
            tvLawyerName.text = "Lawyer: $name"
            // Save for potential fallback use
            lawyerId = intent.getStringExtra("LAWYER_ID")
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

            // Set status color
            when (status.lowercase()) {
                "complete" -> tvStatus.setTextColor(resources.getColor(R.color.completed_status, theme))
                "accepted" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
                "pending" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
                else -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
            }
        }

        intent.getStringExtra("FULL_NAME")?.let { name ->
            // Save for potential fallback use
            clientName = name
        }
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

        // Initialize consultation logs views
        cardViewConsultationLogs = findViewById(R.id.cardViewConsultationLogs)
        tvConsultationLogsTitle = findViewById(R.id.tvConsultationLogsTitle)
        tvConsultationLogsContent = findViewById(R.id.tvConsultationLogsContent)
        tvNoConsultationLogs = findViewById(R.id.tvNoConsultationLogs)

        // Always show the consultation logs card, but with loading state initially
        cardViewConsultationLogs.visibility = View.VISIBLE

        // Set initial value
        tvAppointmentTitle.text = "Appointment Details"
    }

    private fun loadAppointmentDetails() {
        // Get the current user ID
        val userId = auth.currentUser?.uid ?: return
        foundClientId = userId // Save client ID for consultation lookup

        // First try direct lookup by appointmentId - using all valid DB paths
        val appointmentRefs = listOf(
            FirebaseDatabase.getInstance().reference.child("appointments").child(appointmentId),
            FirebaseDatabase.getInstance().reference.child("accepted_appointment").child(appointmentId),
            FirebaseDatabase.getInstance().reference.child("users").child(userId).child("appointments").child(appointmentId)
        )

        var appointmentFound = false
        var attemptCount = 0

        for (ref in appointmentRefs) {
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    attemptCount++

                    if (snapshot.exists() && !appointmentFound) {
                        appointmentFound = true
                        progressBar.visibility = View.GONE

                        // Parse appointment data
                        val appointment = snapshot.getValue(Appointment::class.java)
                        appointment?.let {
                            appointment.appointmentId = appointmentId

                            // Try to get clientId if it's in the data
                            val clientId = snapshot.child("clientId").getValue(String::class.java)
                            if (!clientId.isNullOrEmpty()) {
                                foundClientId = clientId
                            }

                            // Try to get lawyerId if it's in the data
                            val foundLawyerId = snapshot.child("lawyerId").getValue(String::class.java)
                            if (!foundLawyerId.isNullOrEmpty()) {
                                lawyerId = foundLawyerId
                            }

                            displayAppointmentDetails(appointment)
                            loadConsultationLogs(appointment)
                        }
                    } else if (attemptCount >= appointmentRefs.size && !appointmentFound) {
                        // All attempts failed, use intent data
                        progressBar.visibility = View.GONE
                        Log.w("AppointmentDetails", "Appointment not found in any location. Using intent data only.")

                        // Create a fallback appointment from intent data
                        createFallbackAppointmentFromIntent()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    attemptCount++
                    handleDatabaseError(error, "checking appointment locations")

                    // If all attempts failed, fall back to intent data
                    if (attemptCount >= appointmentRefs.size && !appointmentFound) {
                        progressBar.visibility = View.GONE
                        createFallbackAppointmentFromIntent()
                    }
                }
            })
        }
    }

    private fun createFallbackAppointmentFromIntent() {
        // Create a fallback appointment using all intent data
        val fallbackAppointment = Appointment(
            appointmentId = appointmentId,
            lawyerId = intent.getStringExtra("LAWYER_ID") ?: lawyerId ?: "",
            lawyerName = intent.getStringExtra("LAWYER_NAME") ?: "",
            fullName = intent.getStringExtra("FULL_NAME") ?: clientName ?: "",
            date = intent.getStringExtra("DATE") ?: "",
            time = intent.getStringExtra("TIME") ?: "",
            problem = intent.getStringExtra("PROBLEM") ?: "",
            status = intent.getStringExtra("STATUS") ?: "Complete",
            clientId = foundClientId ?: auth.currentUser?.uid ?: "",
            lawyerProfileImage = intent.getStringExtra("LAWYER_PROFILE_IMAGE") ?: ""
        )

        // Use what we have to find the secretary
        findSecretaryByLawyer(fallbackAppointment.lawyerId)

        // Try to load consultation logs based on this fallback data
        loadConsultationLogsByLawyerAndName(
            fallbackAppointment.lawyerId,
            fallbackAppointment.fullName
        )
    }

    private fun displayAppointmentDetails(appointment: Appointment) {
        // Set appointment details in UI
        tvAppointmentTitle.text = "Appointment Details"
        tvAppointmentDate.text = "Date: ${appointment.date ?: "N/A"}"
        tvAppointmentTime.text = "Time: ${appointment.time ?: "N/A"}"
        tvProblem.text = appointment.problem ?: "No problem description provided"
        tvStatus.text = "Status: ${appointment.status ?: "Pending"}"

        // Set status color based on status
        when (appointment.status?.lowercase()) {
            "complete" -> tvStatus.setTextColor(resources.getColor(R.color.completed_status, theme))
            "accepted" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            "pending" -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
            else -> tvStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
        }

        // Load lawyer name if needed
        if (appointment.lawyerName.isNotEmpty()) {
            tvLawyerName.text = "Lawyer: ${appointment.lawyerName}"
        } else {
            // We need to load the lawyer info
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
        }

        // Load secretary name if available
        // Check if secretaryId exists and is not empty
        if (!appointment.secretaryId.isNullOrEmpty()) {
            val secretaryRef = FirebaseDatabase.getInstance().reference
                .child("secretaries")
                .child(appointment.secretaryId)

            secretaryRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        tvSecretaryName.text = "Secretary: $name"
                        tvSecretaryName.visibility = View.VISIBLE
                    } else {
                        tvSecretaryName.text = "Secretary: Not available"
                        tvSecretaryName.visibility = View.VISIBLE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    tvSecretaryName.text = "Secretary: Not available"
                    tvSecretaryName.visibility = View.VISIBLE
                    Log.e("AppointmentDetails", "Error loading secretary: ${error.message}")
                }
            })
        } else {
            // If no secretaryId, check all secretaries to find one matching the law firm
            findSecretaryByLawyer(appointment.lawyerId)
        }
    }

    private fun findSecretaryByLawyer(lawyerId: String) {
        if (lawyerId.isNullOrEmpty()) {
            tvSecretaryName.text = "Secretary: Not assigned"
            tvSecretaryName.visibility = View.VISIBLE
            return
        }

        // First get the lawyer's law firm
        val lawyerRef = FirebaseDatabase.getInstance().reference
            .child("lawyers")
            .child(lawyerId)

        lawyerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawFirm = snapshot.child("lawFirm").getValue(String::class.java)

                    if (!lawFirm.isNullOrEmpty()) {
                        // Now find secretaries in this law firm
                        val secretariesRef = FirebaseDatabase.getInstance().reference
                            .child("secretaries")

                        secretariesRef.orderByChild("lawFirm").equalTo(lawFirm)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(secretariesSnapshot: DataSnapshot) {
                                    if (secretariesSnapshot.exists() && secretariesSnapshot.childrenCount > 0) {
                                        // Get first secretary found
                                        val secretarySnapshot = secretariesSnapshot.children.first()
                                        val secretaryName = secretarySnapshot.child("name").getValue(String::class.java) ?: ""

                                        tvSecretaryName.text = "Secretary: $secretaryName"
                                        tvSecretaryName.visibility = View.VISIBLE
                                    } else {
                                        tvSecretaryName.text = "Secretary: None assigned"
                                        tvSecretaryName.visibility = View.VISIBLE
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("AppointmentDetails", "Error finding secretaries: ${error.message}")
                                    tvSecretaryName.text = "Secretary: Information unavailable"
                                    tvSecretaryName.visibility = View.VISIBLE
                                }
                            })
                    } else {
                        tvSecretaryName.text = "Secretary: Not assigned"
                        tvSecretaryName.visibility = View.VISIBLE
                    }
                } else {
                    tvSecretaryName.text = "Secretary: Not assigned"
                    tvSecretaryName.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppointmentDetails", "Error finding lawyer's law firm: ${error.message}")
                tvSecretaryName.text = "Secretary: Information unavailable"
                tvSecretaryName.visibility = View.VISIBLE
            }
        })
    }

    private fun loadConsultationLogs(appointment: Appointment) {
        loadConsultationLogsByLawyerAndName(appointment.lawyerId, appointment.fullName)
    }

    private fun loadConsultationLogsByLawyerAndName(lawyerId: String, clientName: String) {
        // Log request details for debugging
        Log.d("ConsultationLogs", "Searching for logs with appointmentId=$appointmentId")

        // Directly check all consultations nodes
        val consultationsRef = FirebaseDatabase.getInstance().reference.child("consultations")

        consultationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val consultations = mutableListOf<Consultation>()

                // Look through all consultation entries - ONLY matching by appointmentId
                for (clientSnapshot in snapshot.children) {
                    for (consultationSnapshot in clientSnapshot.children) {
                        val consultation = consultationSnapshot.getValue(Consultation::class.java)
                        val storedAppointmentId = consultationSnapshot.child("appointmentId").getValue(String::class.java)

                        // ONLY add consultations that match this specific appointment ID
                        if (storedAppointmentId == appointmentId && consultation != null) {
                            consultations.add(consultation)
                            Log.d("ConsultationLogs", "Found consultation by appointmentId: $appointmentId")
                        }
                    }
                }

                Log.d("ConsultationLogs", "Total consultations found for this appointment: ${consultations.size}")

                // Display what we found
                displayConsultationsList(consultations)
            }

            override fun onCancelled(error: DatabaseError) {
                handleDatabaseError(error, "loading consultation logs")
                // Show error in logs view
                tvNoConsultationLogs.visibility = View.VISIBLE
                tvNoConsultationLogs.text = "Could not load consultation logs"
                tvConsultationLogsContent.visibility = View.GONE
            }
        })
    }

    private fun matchesClientName(dbName: String, searchName: String): Boolean {
        if (dbName.isEmpty() || searchName.isEmpty()) return false

        // Case 1: Exact match
        if (dbName.equals(searchName, ignoreCase = true)) return true

        // Case 2: One name contains the other
        if (dbName.contains(searchName, ignoreCase = true) ||
            searchName.contains(dbName, ignoreCase = true)) return true

        // Case 3: Compare name parts (first/last name matches)
        val dbParts = dbName.split(" ").filter { it.isNotEmpty() }
        val searchParts = searchName.split(" ").filter { it.isNotEmpty() }

        for (dbPart in dbParts) {
            for (searchPart in searchParts) {
                if (dbPart.equals(searchPart, ignoreCase = true) &&
                    dbPart.length > 2) { // At least 3 chars to avoid matching common words
                    return true
                }
            }
        }

        return false
    }

    private fun displayConsultationsList(consultations: List<Consultation>) {
        Log.d("ConsultationLogs", "Displaying ${consultations.size} consultation logs for appointment: $appointmentId")

        if (consultations.isEmpty()) {
            tvNoConsultationLogs.visibility = View.VISIBLE
            tvNoConsultationLogs.text = "No consultation logs available for this appointment"
            tvConsultationLogsContent.visibility = View.GONE
            return
        }

        // Sort consultations by date (newest first)
        val sortedConsultations = consultations.sortedByDescending {
            try {
                val formatter = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault())
                formatter.parse(it.consultationDate)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        // Build the consultation logs text
        val logsBuilder = StringBuilder()
        logsBuilder.append("Consultation logs for this appointment:\n\n")

        for (consultation in sortedConsultations) {
            logsBuilder.append("Date: ${consultation.consultationDate}\n")
            logsBuilder.append("Time: ${consultation.consultationTime}\n")
            if (!consultation.consultationType.isNullOrEmpty()) {
                logsBuilder.append("Type: ${consultation.consultationType}\n")
            }
            logsBuilder.append("Notes: ${consultation.notes}\n")
            logsBuilder.append("\n--------------------\n\n")
        }

        // Update UI
        tvConsultationLogsTitle.text = "Consultation Logs (${consultations.size})"
        tvConsultationLogsContent.text = logsBuilder.toString()
        tvNoConsultationLogs.visibility = View.GONE
        tvConsultationLogsContent.visibility = View.VISIBLE
    }

    private fun handleDatabaseError(error: DatabaseError, operation: String) {
        Log.e("AppointmentDetails", "Error $operation: ${error.message}")
    }
}