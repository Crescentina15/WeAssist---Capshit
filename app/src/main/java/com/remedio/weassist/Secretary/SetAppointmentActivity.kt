package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
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

        // Initialize views
        lawyerId = intent.getStringExtra("LAWYER_ID")
        dateSpinner = findViewById(R.id.date_spinner)
        timeSpinner = findViewById(R.id.time_spinner)
        editFullName = findViewById(R.id.edit_full_name)
        editProblem = findViewById(R.id.edit_problem)
        btnSetAppointment = findViewById(R.id.btn_set_appointment)
        backArrow = findViewById(R.id.back_arrow)

        // Back button
        backArrow.setOnClickListener { finish() }

        // Get current user
        val currentUser = FirebaseAuth.getInstance().currentUser
        clientId = currentUser?.uid

        if (clientId != null) {
            fetchClientName(clientId!!)
        } else {
            Log.e("SetAppointmentActivity", "User not logged in")
        }

        if (lawyerId != null) {
            fetchAvailability(lawyerId!!)
        }

        btnSetAppointment.setOnClickListener { saveAppointment() }
    }

    private fun fetchClientName(clientId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(clientId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java)
                    if (!name.isNullOrEmpty()) {
                        editFullName.setText(name) // Auto-fill name field
                        Log.d("SetAppointmentActivity", "Client name retrieved: $name")
                    } else {
                        Log.e("SetAppointmentActivity", "Client name is empty")
                    }
                } else {
                    Log.e("SetAppointmentActivity", "User snapshot does not exist")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SetAppointmentActivity", "Failed to load client name: ${error.message}")
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
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
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
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
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
                "problem" to problem
            )

            appointmentRef.child(appointmentId).setValue(appointmentData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Appointment set successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to set appointment", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
