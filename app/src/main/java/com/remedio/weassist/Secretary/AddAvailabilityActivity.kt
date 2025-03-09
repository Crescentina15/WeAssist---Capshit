package com.remedio.weassist.Secretary

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.R
import java.util.*

class AddAvailabilityActivity : AppCompatActivity() {

    private lateinit var lawyerId: String
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_availability)

        val lawyerNameTextView: TextView = findViewById(R.id.lawyer_name)
        val selectDate: EditText = findViewById(R.id.select_date)
        val startTime: EditText = findViewById(R.id.start_time)
        val endTime: EditText = findViewById(R.id.end_time)
        val addAvailabilityButton: Button = findViewById(R.id.add_availability_button)

        // Get lawyer details from intent
        val lawyerName = intent.getStringExtra("LAWYER_NAME") ?: "Unknown"
        lawyerId = intent.getStringExtra("LAWYER_ID") ?: ""

        lawyerNameTextView.text = "Adding Availability for $lawyerName"

        // Initialize Firebase Realtime Database (Fixed reference to lowercase "lawyers")
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        // Date picker
        selectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedCalendar = Calendar.getInstance()
                    selectedCalendar.set(year, month, dayOfMonth)
                    if (selectedCalendar.before(calendar)) {
                        Toast.makeText(this, "You cannot select past dates", Toast.LENGTH_SHORT).show()
                    } else {
                        selectDate.setText("$year-${month + 1}-$dayOfMonth")
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = calendar.timeInMillis // Prevent past dates
            datePicker.show()
        }

        // Time pickers
        startTime.setOnClickListener { showTimePicker(startTime) }
        endTime.setOnClickListener { showTimePicker(endTime) }

        // Add availability button click
        addAvailabilityButton.setOnClickListener {
            val date = selectDate.text.toString().trim()
            val start = startTime.text.toString().trim()
            val end = endTime.text.toString().trim()

            if (date.isEmpty() || start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            } else {
                saveAvailabilityToDatabase(date, start, end)
            }
        }
    }

    private fun showTimePicker(editText: EditText) {
        val calendar = Calendar.getInstance()
        val timePicker = TimePickerDialog(this, { _, hour, minute ->
            editText.setText(String.format("%02d:%02d", hour, minute))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
        timePicker.show()
    }

    private fun saveAvailabilityToDatabase(date: String, start: String, end: String) {
        val availabilityRef = database.child(lawyerId).child("availability")

        availabilityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (availabilitySnapshot in snapshot.children) {
                    val existingDate = availabilitySnapshot.child("date").value.toString()
                    val existingStart = availabilitySnapshot.child("startTime").value.toString()
                    val existingEnd = availabilitySnapshot.child("endTime").value.toString()

                    // Check if the same date and overlapping time slot exists
                    if (existingDate == date &&
                        ((start >= existingStart && start < existingEnd) || (end > existingStart && end <= existingEnd))
                    ) {
                        Toast.makeText(
                            applicationContext,
                            "Time slot already taken",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                }

                // Generate a unique ID for availability entry
                val availabilityId = availabilityRef.push().key ?: return
                val availability = mapOf(
                    "id" to availabilityId,  // Include unique ID for easier reference
                    "date" to date,
                    "startTime" to start,
                    "endTime" to end
                )

                availabilityRef.child(availabilityId).setValue(availability)
                    .addOnSuccessListener {
                        Toast.makeText(
                            applicationContext,
                            "Availability added successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            applicationContext,
                            "Failed to add availability",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    applicationContext,
                    "Database error: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
