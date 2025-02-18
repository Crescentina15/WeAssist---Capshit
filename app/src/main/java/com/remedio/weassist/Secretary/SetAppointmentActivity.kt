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
    private lateinit var availabilityGrid: GridLayout
    private lateinit var dateContainer: LinearLayout
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

        availabilityGrid = findViewById(R.id.availability_grid)
        dateContainer = findViewById(R.id.date_container)
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
                dateContainer.removeAllViews()
                availabilityGrid.removeAllViews()

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

                    createDateCheckBoxes(dateMap)
                } else {
                    Toast.makeText(applicationContext, "No availability found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load availability", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createDateCheckBoxes(dateMap: Map<String, List<String>>) {
        dateMap.forEach { (date, timeSlots) ->
            val dateCheckBox = CheckBox(this)
            dateCheckBox.text = formatDate(date)
            dateCheckBox.setTextColor(resources.getColor(android.R.color.black))

            dateCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedDate = date

                    // Uncheck other checkboxes
                    for (i in 0 until dateContainer.childCount) {
                        val child = dateContainer.getChildAt(i)
                        if (child is CheckBox && child != dateCheckBox) {
                            child.isChecked = false
                        }
                    }

                    updateTimeSlots(timeSlots)
                }
            }

            dateContainer.addView(dateCheckBox)
        }
    }

    private fun updateTimeSlots(timeSlots: List<String>) {
        availabilityGrid.removeAllViews()
        for (slot in timeSlots) {
            val checkBox = CheckBox(this)
            checkBox.text = slot
            checkBox.textSize = 14f
            checkBox.setTextColor(resources.getColor(android.R.color.black))

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTime = slot

                    // Uncheck other time checkboxes
                    for (i in 0 until availabilityGrid.childCount) {
                        val child = availabilityGrid.getChildAt(i)
                        if (child is CheckBox && child != checkBox) {
                            child.isChecked = false
                        }
                    }
                }
            }

            availabilityGrid.addView(checkBox)
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

        // Reference to the general appointments node
        val appointmentRef = FirebaseDatabase.getInstance()
            .getReference("appointments")

        val appointmentId = appointmentRef.push().key

        if (appointmentId != null) {
            val appointmentData = mapOf(
                "appointmentId" to appointmentId,
                "lawyerId" to lawyerId,  // Keeping lawyerId as part of appointment details
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

    private fun formatDate(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: date)
        } catch (e: Exception) {
            date
        }
    }
}
