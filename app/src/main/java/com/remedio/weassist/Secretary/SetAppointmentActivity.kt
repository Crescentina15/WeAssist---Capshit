package com.remedio.weassist.Secretary

import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SetAppointmentActivity : AppCompatActivity() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var availabilityGrid: GridLayout
    private var lawyerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_appointment)

        // Retrieve lawyer ID from intent
        lawyerId = intent.getStringExtra("LAWYER_ID")

        // Find GridLayout in XML
        availabilityGrid = findViewById(R.id.availability_grid)

        if (lawyerId != null) {
            fetchAvailability(lawyerId!!)
        }
    }

    private fun fetchAvailability(lawyerId: String) {
        databaseReference = FirebaseDatabase.getInstance()
            .getReference("lawyers")
            .child(lawyerId)
            .child("availability")

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dateContainer: LinearLayout = findViewById(R.id.date_container)
                dateContainer.removeAllViews() // Clear existing date buttons
                availabilityGrid.removeAllViews() // Clear existing time slot buttons

                if (snapshot.exists()) {
                    var hasDate = false
                    for (availabilitySnapshot in snapshot.children) {
                        val date = availabilitySnapshot.child("date").getValue(String::class.java)
                        val startTime =
                            availabilitySnapshot.child("startTime").getValue(String::class.java)
                        val endTime =
                            availabilitySnapshot.child("endTime").getValue(String::class.java)

                        // Add date button
                        if (date != null) {
                            hasDate = true
                            val dateButton = Button(this@SetAppointmentActivity)
                            dateButton.text = formatDate(date)  // Format the date (e.g., "13 MON")
                            dateButton.setTextColor(resources.getColor(android.R.color.white))
                            dateButton.setBackgroundResource(R.drawable.rounded_bottom)
                            dateButton.setPadding(5, 5, 5, 5)

                            val params = LinearLayout.LayoutParams(
                                resources.getDimensionPixelSize(R.dimen.date_button_width),
                                resources.getDimensionPixelSize(R.dimen.date_button_height)
                            )
                            params.setMargins(8, 8, 8, 8)
                            dateButton.layoutParams = params

                            dateButton.setOnClickListener {
                                // Handle date button click (Optional)
                                Toast.makeText(
                                    this@SetAppointmentActivity,
                                    "Selected date: $date",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            // Add the new button to the date container
                            dateContainer.addView(dateButton)
                        }

                        // Add time slot button only if both startTime and endTime are present
                        if (startTime != null && endTime != null) {
                            generateTimeSlotButton(startTime, endTime)
                        }
                    }

                    // If no dates or time slots are available
                    if (!hasDate) {
                        Toast.makeText(
                            applicationContext,
                            "No availability found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "No availability found", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    applicationContext,
                    "Failed to load availability",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun formatDate(date: String): String {
        // Example of date formatting if needed (e.g., "13 MON")
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        return try {
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: date)
        } catch (e: ParseException) {
            date // Return the original date if parsing fails
        }
    }

    private fun generateTimeSlotButton(startTime: String, endTime: String) {
        val timeSlot = "$startTime - $endTime" // Combine start and end time

        val button = Button(this)
        button.text = timeSlot
        button.textSize = 14f
        button.setBackgroundResource(R.drawable.rounded_bottom)
        button.setTextColor(resources.getColor(android.R.color.white))
        button.setPadding(5, 5, 5, 5)

        val params = GridLayout.LayoutParams()
        params.width = GridLayout.LayoutParams.WRAP_CONTENT
        params.height = GridLayout.LayoutParams.WRAP_CONTENT
        params.setMargins(8, 8, 8, 8)

        button.layoutParams = params
        availabilityGrid.addView(button)
    }
}
