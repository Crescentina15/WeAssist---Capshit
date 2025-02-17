package com.remedio.weassist.Secretary

import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
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
                availabilityGrid.removeAllViews()

                if (snapshot.exists()) {
                    for (availabilitySnapshot in snapshot.children) {
                        val date = availabilitySnapshot.child("date").getValue(String::class.java)
                        val startTime = availabilitySnapshot.child("startTime").getValue(String::class.java)
                        val endTime = availabilitySnapshot.child("endTime").getValue(String::class.java)

                        if (date != null && startTime != null && endTime != null) {
                            generateTimeSlotButton(startTime, endTime)
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "No availability found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load availability", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Modify the generateTimeSlots method to create a button with start and end time combined
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
