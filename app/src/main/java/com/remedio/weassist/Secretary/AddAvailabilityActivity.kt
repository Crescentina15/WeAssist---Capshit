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

        // Date picker with month/date/year format
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
                        // Format as month/date/year
                        // For two-digit month and day format (03/07/2025)
                        selectDate.setText(String.format("%02d/%02d/%d", month + 1, dayOfMonth, year))
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
            // Convert to 12-hour format with AM/PM
            val hourOfDay = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            editText.setText(String.format("%d:%02d %s", hourOfDay, minute, amPm))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
        timePicker.show()
    }

    private fun saveAvailabilityToDatabase(date: String, start: String, end: String) {
        val availabilityRef = database.child(lawyerId).child("availability")

        availabilityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Parse the new times to compare
                val newStartTime = parseTimeToMinutes(start)
                val newEndTime = parseTimeToMinutes(end)

                // Make sure end time is after start time
                if (newEndTime <= newStartTime) {
                    Toast.makeText(
                        applicationContext,
                        "End time must be after start time",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                for (availabilitySnapshot in snapshot.children) {
                    val existingDate = availabilitySnapshot.child("date").value.toString()
                    val existingStart = availabilitySnapshot.child("startTime").value.toString()
                    val existingEnd = availabilitySnapshot.child("endTime").value.toString()

                    // Only check overlaps on the same date
                    if (existingDate == date) {
                        val existingStartTime = parseTimeToMinutes(existingStart)
                        val existingEndTime = parseTimeToMinutes(existingEnd)

                        // Check for any type of overlap
                        if (!(newEndTime <= existingStartTime || newStartTime >= existingEndTime)) {
                            Toast.makeText(
                                applicationContext,
                                "Time slot overlaps with existing availability",
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }
                    }
                }

                // Generate a unique ID for availability entry
                val availabilityId = availabilityRef.push().key ?: return
                val availability = mapOf(
                    "id" to availabilityId,
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

    // Helper function to convert time strings to minutes since midnight for comparison
    private fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        val hourMinParts = parts[1].split(" ")

        var hour = parts[0].toInt()
        val minute = hourMinParts[0].toInt()
        val amPm = hourMinParts[1]

        // Convert to 24-hour format for calculation
        if (amPm.equals("PM", ignoreCase = true) && hour < 12) {
            hour += 12
        } else if (amPm.equals("AM", ignoreCase = true) && hour == 12) {
            hour = 0
        }

        return hour * 60 + minute
    }
}
