package com.remedio.weassist.Clients

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*

class ClientNotificationActivity : AppCompatActivity() {

    private lateinit var recentTitle: TextView
    private lateinit var recentNotification: TextView
    private lateinit var recentTime: TextView
    private lateinit var earlierNotification: TextView
    private lateinit var earlierTime: TextView

    private lateinit var databaseReference: DatabaseReference
    private var clientId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification) // Ensure this XML file exists

        // Initialize UI elements
        recentTitle = findViewById(R.id.recentTitle)
        recentNotification = findViewById(R.id.recentNotification)
        recentTime = findViewById(R.id.recentTime)
        earlierNotification = findViewById(R.id.earlierNotification)
        earlierTime = findViewById(R.id.earlierTime)

        // Get current client ID
        clientId = FirebaseAuth.getInstance().currentUser?.uid

        if (clientId != null) {
            fetchNotifications(clientId!!)
        } else {
            Log.e("ClientNotificationActivity", "Client ID is null, user not logged in")
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchNotifications(clientId: String) {
        databaseReference = FirebaseDatabase.getInstance().getReference("notifications").child(clientId)

        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val notifications = mutableListOf<Triple<String, Long, String>>() // message, timestamp, key

                    for (notif in snapshot.children) {
                        val message = notif.child("message").getValue(String::class.java)
                        val timestampValue = notif.child("timestamp").value
                        val notifKey = notif.key // Store the Firebase key

                        val timestamp = when (timestampValue) {
                            is Long -> timestampValue
                            is String -> timestampValue.toLongOrNull()
                            else -> null
                        }

                        if (!message.isNullOrEmpty() && timestamp != null && notifKey != null) {
                            notifications.add(Triple(message, timestamp, notifKey))
                        }
                    }

                    if (notifications.isNotEmpty()) {
                        // Sort notifications by timestamp (latest first)
                        notifications.sortByDescending { it.second }

                        // Keep only the latest two notifications
                        while (notifications.size > 2) {
                            val oldestNotif = notifications.removeLast() // Remove the oldest
                            databaseReference.child(oldestNotif.third).removeValue() // Delete from Firebase
                        }

                        // Display the most recent notification
                        val latestNotif = notifications.first()
                        recentNotification.text = latestNotif.first
                        recentTime.text = formatTimestamp(latestNotif.second)

                        // Display an earlier notification (if available)
                        if (notifications.size > 1) {
                            val earlierNotif = notifications[1]
                            earlierNotification.text = earlierNotif.first
                            earlierTime.text = formatTimestamp(earlierNotif.second)
                        } else {
                            earlierNotification.text = "No earlier notifications"
                            earlierTime.text = ""
                        }
                    } else {
                        // No notifications available
                        recentNotification.text = "No recent notifications"
                        recentTime.text = ""
                        earlierNotification.text = "No earlier notifications"
                        earlierTime.text = ""
                    }
                } else {
                    Log.d("ClientNotificationActivity", "No notifications found")
                    recentNotification.text = "No recent notifications"
                    recentTime.text = ""
                    earlierNotification.text = "No earlier notifications"
                    earlierTime.text = ""
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ClientNotificationActivity", "Firebase error: ${error.message}")
                Toast.makeText(applicationContext, "Failed to load notifications", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
