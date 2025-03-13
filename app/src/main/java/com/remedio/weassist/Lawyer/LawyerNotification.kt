package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.NotificationAdapter
import com.remedio.weassist.Models.NotificationItem
import com.remedio.weassist.R

class LawyerNotification : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var notificationAdapter: NotificationAdapter
    private val notifications = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_notification)

        // Initialize UI components
        recyclerView = findViewById(R.id.rvNotifications1)
        emptyView = findViewById(R.id.tvNoNotifications1)
        val backButton = findViewById<ImageButton>(R.id.back_button1)

        // Set back button click listener
        backButton.setOnClickListener {
            finish()
        }

        // Initialize RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance()
        val lawyerId = auth.currentUser?.uid

        if (lawyerId != null) {
            fetchNotifications(lawyerId)
        } else {
            Log.e("LawyerNotification", "Lawyer ID is null")
            Toast.makeText(this, "Lawyer not logged in", Toast.LENGTH_SHORT).show()
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun fetchNotifications(lawyerId: String) {
        database = FirebaseDatabase.getInstance().getReference("notifications").child(lawyerId)

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notificationsList = mutableListOf<NotificationItem>()

                for (notificationSnapshot in snapshot.children) {
                    try {
                        val id = notificationSnapshot.key ?: ""
                        val senderId = notificationSnapshot.child("senderId").getValue(String::class.java) ?: ""
                        val senderName = notificationSnapshot.child("senderName").getValue(String::class.java) ?: "Unknown"
                        val message = notificationSnapshot.child("message").getValue(String::class.java) ?: ""
                        val timestamp = notificationSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        val type = notificationSnapshot.child("type").getValue(String::class.java) ?: ""
                        val isRead = notificationSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                        val appointmentId = notificationSnapshot.child("appointmentId").getValue(String::class.java)

                        val notification = NotificationItem(
                            id = id,
                            senderId = senderId,
                            senderName = senderName,
                            message = message,
                            timestamp = timestamp,
                            type = type,
                            isRead = isRead,
                            appointmentId = appointmentId
                        )

                        notificationsList.add(notification)
                    } catch (e: Exception) {
                        Log.e("LawyerNotification", "Error parsing notification: ${e.message}")
                    }
                }

                // Sort notifications by timestamp (newest first)
                notificationsList.sortByDescending { it.timestamp }

                // Update UI based on notifications
                if (notificationsList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    // Initialize adapter with the notification click handler
                    notificationAdapter = NotificationAdapter(notificationsList) { notification ->
                        // Mark notification as read when clicked
                        markNotificationAsRead(lawyerId, notification.id)

                        // Handle notification click based on type
                        when (notification.type) {
                            "appointment_accepted" -> {
                                // You could navigate to appointment details here if needed
                                Toast.makeText(this@LawyerNotification,
                                    "Appointment accepted", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                // Default handling for other notification types
                                Toast.makeText(this@LawyerNotification,
                                    "Notification clicked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    recyclerView.adapter = notificationAdapter
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LawyerNotification", "Failed to fetch notifications: ${error.message}")
                Toast.makeText(this@LawyerNotification, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        })
    }

    private fun markNotificationAsRead(userId: String, notificationId: String) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications")
            .child(userId).child(notificationId)

        // Update the isRead field to true
        notificationRef.child("isRead").setValue(true)
            .addOnSuccessListener {
                Log.d("Notification", "Notification marked as read")
            }
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to mark notification as read: ${e.message}")
            }
    }
}