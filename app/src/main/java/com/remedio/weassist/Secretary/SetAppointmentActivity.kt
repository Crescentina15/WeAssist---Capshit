package com.remedio.weassist.Secretary

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*

class SetAppointmentActivity : AppCompatActivity() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var dateSpinner: Spinner
    private lateinit var timeSpinner: Spinner
    private lateinit var editFullName: EditText
    private lateinit var editProblem: EditText
    private lateinit var btnSetAppointment: Button

    private var lawyerId: String? = null
    private var selectedDate: String? = null
    private var selectedTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_appointment)

        lawyerId = intent.getStringExtra("LAWYER_ID")

        dateSpinner = findViewById(R.id.date_spinner)
        timeSpinner = findViewById(R.id.time_spinner)
        editFullName = findViewById(R.id.edit_full_name)
        editProblem = findViewById(R.id.edit_problem)
        btnSetAppointment = findViewById(R.id.btn_set_appointment)

        if (lawyerId != null) {
            fetchAvailability(lawyerId!!)
        }

        btnSetAppointment.setOnClickListener {
            saveAppointment()
        }
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
        val dates = dateMap.keys.toList()
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dates)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateSpinner.adapter = dateAdapter

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                selectedDate = dates[position]
                updateTimeSpinner(dateMap[selectedDate] ?: emptyList())
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun updateTimeSpinner(timeSlots: List<String>) {
        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeSlots)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timeSpinner.adapter = timeAdapter

        timeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                selectedTime = timeSlots[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
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

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val clientId = currentUser?.uid

        if (clientId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Reference to the general appointments node
        val appointmentRef = FirebaseDatabase.getInstance()
            .getReference("appointments")

        val appointmentId = appointmentRef.push().key

        if (appointmentId != null) {
            val appointmentData = mapOf(
                "appointmentId" to appointmentId,
                "clientId" to clientId,  // **Added clientId**
                "lawyerId" to lawyerId,
                "date" to selectedDate,
                "time" to selectedTime,
                "fullName" to fullName,
                "problem" to problem
            )

            // Save in the general appointments node
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