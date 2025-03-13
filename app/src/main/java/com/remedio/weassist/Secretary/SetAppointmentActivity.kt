package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class SetAppointmentActivity : AppCompatActivity() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var dateSpinner: Spinner
    private lateinit var timeSpinner: Spinner
    private lateinit var editFullName: EditText
    private lateinit var editProblem: EditText
    private lateinit var btnSetAppointment: Button
    private lateinit var backArrow: ImageButton

    private var lawyerId: String? = null
    private var selectedDate: String? = null
    private var selectedTime: String? = null
    private var clientId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_appointment)

        lawyerId = intent.getStringExtra("LAWYER_ID")

        dateSpinner = findViewById(R.id.date_spinner)
        timeSpinner = findViewById(R.id.time_spinner)
        editFullName = findViewById(R.id.edit_full_name)
        editProblem = findViewById(R.id.edit_problem)
        btnSetAppointment = findViewById(R.id.btn_set_appointment)
        backArrow = findViewById(R.id.back_arrow)

        clientId = FirebaseAuth.getInstance().currentUser?.uid

        backArrow.setOnClickListener {
            finish()
        }

        if (clientId != null) {
            fetchClientName(clientId!!)
        } else {
            Log.e("SetAppointmentActivity", "Client ID is null, user not logged in")
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        if (lawyerId != null) {
            fetchAvailability(lawyerId!!)
        } else {
            Log.e("SetAppointmentActivity", "Lawyer ID is null")
            Toast.makeText(this, "Lawyer not found", Toast.LENGTH_SHORT).show()
        }

        btnSetAppointment.setOnClickListener {
            saveAppointment()
        }
    }

    private fun fetchClientName(clientId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(clientId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java)
                    val lastName = snapshot.child("lastName").getValue(String::class.java)

                    if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty()) {
                        val fullName = "$firstName $lastName"
                        editFullName.setText(fullName)
                    } else {
                        Toast.makeText(applicationContext, "Client name not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load client name", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchAvailability(lawyerId: String) {
        databaseReference = FirebaseDatabase.getInstance()
            .getReference("lawyers")
            .child(lawyerId)
            .child("availability")

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val dateMap = mutableMapOf<String, MutableList<String>>()

                    for (availabilitySnapshot in snapshot.children) {
                        val date = availabilitySnapshot.child("date").getValue(String::class.java)
                        val startTime = availabilitySnapshot.child("startTime").getValue(String::class.java)
                        val endTime = availabilitySnapshot.child("endTime").getValue(String::class.java)

                        if (date != null && startTime != null && endTime != null) {
                            val timeSlot = "$startTime - $endTime"
                            dateMap.putIfAbsent(date, mutableListOf())
                            dateMap[date]?.add(timeSlot)
                        }
                    }
                    setupDateSpinner(dateMap)
                } else {
                    Toast.makeText(applicationContext, "No availability found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load availability", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupDateSpinner(dateMap: Map<String, List<String>>) {
        val dates = mutableListOf("Select Date") + dateMap.keys
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dates)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateSpinner.adapter = dateAdapter

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    selectedDate = dates[position]
                    updateTimeSpinner(dateMap[selectedDate] ?: emptyList())
                } else {
                    selectedDate = null
                    updateTimeSpinner(emptyList())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateTimeSpinner(timeSlots: List<String>) {
        val times = mutableListOf("Select Time") + timeSlots
        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, times)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timeSpinner.adapter = timeAdapter

        timeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedTime = if (position > 0) times[position] else null
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun saveAppointment() {
        val fullName = editFullName.text.toString().trim()
        val problem = editProblem.text.toString().trim()

        if (lawyerId == null || selectedDate == null || selectedTime == null) {
            Toast.makeText(this, "Please select a date and time", Toast.LENGTH_SHORT).show()
            return
        }

        if (fullName.isEmpty() || problem.isEmpty()) {
            Toast.makeText(this, "Please enter full name and problem", Toast.LENGTH_SHORT).show()
            return
        }

        if (clientId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val appointmentRef = FirebaseDatabase.getInstance().getReference("appointments")
        val appointmentId = appointmentRef.push().key

        if (appointmentId != null) {
            val appointmentData = mapOf(
                "appointmentId" to appointmentId,
                "clientId" to clientId,
                "lawyerId" to lawyerId,
                "date" to selectedDate,
                "time" to selectedTime,
                "fullName" to fullName,
                "problem" to problem,
                "status" to "Pending"
            )

            appointmentRef.child(appointmentId).setValue(appointmentData)
                .addOnSuccessListener {
                    // Removed the sendNotificationToClient call

                    // Notify the lawyer about the new appointment
                    sendNotificationToLawyer(lawyerId!!, selectedDate!!, selectedTime!!)

                    // Send notification to all secretaries associated with this lawyer
                    sendNotificationToSecretaries(lawyerId!!, selectedDate!!, selectedTime!!, appointmentId)

                    // Update the lawyer's availability and refresh spinners
                    updateLawyerAvailability(lawyerId!!, selectedDate!!, selectedTime!!)

                    Toast.makeText(this, "Appointment set successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to set appointment", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateLawyerAvailability(lawyerId: String, date: String, time: String) {
        val availabilityRef = FirebaseDatabase.getInstance()
            .getReference("lawyers")
            .child(lawyerId)
            .child("availability")

        availabilityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (availabilitySnapshot in snapshot.children) {
                        val availabilityDate = availabilitySnapshot.child("date").getValue(String::class.java)
                        val startTime = availabilitySnapshot.child("startTime").getValue(String::class.java)
                        val endTime = availabilitySnapshot.child("endTime").getValue(String::class.java)

                        if (availabilityDate == date && "$startTime - $endTime" == time) {
                            availabilitySnapshot.ref.removeValue().addOnSuccessListener {
                                // Refresh the availability spinners
                                fetchAvailability(lawyerId)
                            }
                            break
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SetAppointmentActivity", "Failed to update lawyer availability: ${error.message}")
            }
        })
    }

    private fun sendNotificationToLawyer(lawyerId: String, date: String, time: String) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(lawyerId)
        val notificationId = notificationRef.push().key

        if (notificationId != null) {
            val currentTimestamp = System.currentTimeMillis().toString()

            val notificationData = mapOf(
                "notificationId" to notificationId,
                "message" to "Your appointment on $date at $time has been accepted.",
                "timestamp" to currentTimestamp
            )

            notificationRef.child("recent").setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("SetAppointmentActivity", "Recent appointment updated for lawyer: $lawyerId")

                    // Move previous "recent" to "earlier" before replacing it
                    notificationRef.child("recent").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val previousData = snapshot.value
                                notificationRef.child("earlier").setValue(previousData)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("SetAppointmentActivity", "Failed to update earlier notification: ${error.message}")
                        }
                    })
                }
                .addOnFailureListener {
                    Log.e("SetAppointmentActivity", "Failed to update notification")
                }
        }
    }


    private fun sendNotificationToSecretaries(lawyerId: String, date: String, time: String, appointmentId: String) {
        // Get the secretaryID for this lawyer
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        lawyerRef.child("secretaryID").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val secretaryId = snapshot.getValue(String::class.java)
                    if (secretaryId != null && secretaryId.isNotEmpty()) {
                        // Send notification to the secretary
                        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(secretaryId)
                        val notificationId = notificationRef.push().key

                        if (notificationId != null) {
                            // Get client name for the notification message
                            val clientName = editFullName.text.toString().trim()

                            val notificationData = mapOf(
                                "id" to notificationId,  // Changed from "notificationId" to match your NotificationItem class
                                "senderId" to clientId,
                                "message" to "New appointment: $clientName on $date at $time",
                                "timestamp" to System.currentTimeMillis(),  // Using Long directly instead of String
                                "type" to "appointment",
                                "isRead" to false,
                                "appointmentId" to appointmentId // For navigation to appointment details
                            )

                            notificationRef.child(notificationId).setValue(notificationData)
                                .addOnSuccessListener {
                                    Log.d("SetAppointmentActivity", "Notification sent to secretary: $secretaryId")
                                }
                                .addOnFailureListener {
                                    Log.e("SetAppointmentActivity", "Failed to send notification to secretary: ${it.message}")
                                }
                        }
                    } else {
                        Log.d("SetAppointmentActivity", "No secretary associated with this lawyer")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SetAppointmentActivity", "Failed to fetch secretary ID: ${error.message}")
            }
        })
    }
}