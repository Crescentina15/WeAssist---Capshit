package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class LawyerNotification : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var recentNotificationTextView: TextView
    private lateinit var recentTimeTextView: TextView
    private lateinit var earlierNotificationTextView: TextView
    private lateinit var earlierTimeTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_notification) // Ensure this matches your XML layout file name

        // Initialize TextViews
        recentNotificationTextView = findViewById(R.id.recentNotification)
        recentTimeTextView = findViewById(R.id.recentTime)
        earlierNotificationTextView = findViewById(R.id.earlierNotification)
        earlierTimeTextView = findViewById(R.id.earlierTime)

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance()
        val lawyerId = auth.currentUser?.uid

        if (lawyerId != null) {
            fetchNotifications(lawyerId)
        } else {
            Log.e("LawyerNotification", "Lawyer ID is null")
            Toast.makeText(this, "Lawyer not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchNotifications(lawyerId: String) {
        database = FirebaseDatabase.getInstance().getReference("notifications").child(lawyerId)

        // Fetch Recent Notification
        database.child("recent").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val message = snapshot.child("message").getValue(String::class.java) ?: "No recent appointments."
                    val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: ""
                    val formattedTimestamp = formatTimestamp(timestamp)

                    recentNotificationTextView.text = message
                    recentTimeTextView.text = formattedTimestamp
                } else {
                    recentNotificationTextView.text = "No recent appointments."
                    recentTimeTextView.text = ""
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LawyerNotification", "Failed to fetch recent notifications: ${error.message}")
                Toast.makeText(this@LawyerNotification, "Failed to load recent notifications", Toast.LENGTH_SHORT).show()
            }
        })

        // Fetch Earlier Notification
        database.child("earlier").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val message = snapshot.child("message").getValue(String::class.java) ?: "No earlier appointments."
                    val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: ""
                    val formattedTimestamp = formatTimestamp(timestamp)

                    earlierNotificationTextView.text = message
                    earlierTimeTextView.text = formattedTimestamp
                } else {
                    earlierNotificationTextView.text = "No earlier appointments."
                    earlierTimeTextView.text = ""
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LawyerNotification", "Failed to fetch earlier notifications: ${error.message}")
                Toast.makeText(this@LawyerNotification, "Failed to load earlier notifications", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Helper function to format timestamp into a readable date-time
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault())
            val date = java.util.Date(timestamp.toLong())
            sdf.format(date)
        } catch (e: Exception) {
            "Invalid date"
        }
    }
}