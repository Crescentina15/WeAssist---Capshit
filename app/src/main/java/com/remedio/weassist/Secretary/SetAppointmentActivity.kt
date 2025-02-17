package com.remedio.weassist.Secretary

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class SetAppointmentActivity : AppCompatActivity() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var availabilityGrid: GridLayout
    private var lawyerId: String? = null
    private lateinit var dateContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_appointment)

        lawyerId = intent.getStringExtra("LAWYER_ID")

        availabilityGrid = findViewById(R.id.availability_grid)
        dateContainer = findViewById(R.id.date_container)

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
                dateContainer.removeAllViews()
                availabilityGrid.removeAllViews()

                if (snapshot.exists()) {
                    var hasDate = false
                    val dateMap = mutableMapOf<String, MutableList<String>>()

                    for (availabilitySnapshot in snapshot.children) {
                        val date = availabilitySnapshot.child("date").getValue(String::class.java)
                        val startTime = availabilitySnapshot.child("startTime").getValue(String::class.java)
                        val endTime = availabilitySnapshot.child("endTime").getValue(String::class.java)

                        if (date != null && startTime != null && endTime != null) {
                            hasDate = true
                            val timeSlot = "$startTime - $endTime"
                            dateMap.putIfAbsent(date, mutableListOf())
                            dateMap[date]?.add(timeSlot)
                        }
                    }

                    if (!hasDate) {
                        Toast.makeText(applicationContext, "No availability found", Toast.LENGTH_SHORT).show()
                    } else {
                        createDateCheckBoxes(dateMap)
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

    private fun createDateCheckBoxes(dateMap: Map<String, List<String>>) {
        dateMap.forEach { (date, timeSlots) ->
            val dateCheckBox = CheckBox(this)
            dateCheckBox.text = formatDate(date)
            dateCheckBox.setTextColor(resources.getColor(android.R.color.black))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(10, 8, 10, 8)
            dateCheckBox.layoutParams = params
            dateCheckBox.setPadding(10, 10, 10, 10)

            dateCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Uncheck other checkboxes
                    for (i in 0 until dateContainer.childCount) {
                        val child = dateContainer.getChildAt(i)
                        if (child is CheckBox && child != dateCheckBox) {
                            child.isChecked = false
                        }
                    }

                    // Display available time slots for the selected date
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

            val params = GridLayout.LayoutParams()
            params.width = GridLayout.LayoutParams.WRAP_CONTENT
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.setMargins(8, 8, 8, 8)

            checkBox.layoutParams = params
            availabilityGrid.addView(checkBox)
        }
    }

    private fun formatDate(date: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        return try {
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: date)
        } catch (e: ParseException) {
            date
        }
    }
}
