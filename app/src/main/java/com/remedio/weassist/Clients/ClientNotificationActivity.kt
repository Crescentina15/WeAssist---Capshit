package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.Models.NotificationAdapter
import com.remedio.weassist.Models.NotificationItem
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*

class ClientNotificationActivity : AppCompatActivity() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var tvNoNotifications: TextView
    private lateinit var backButton: ImageButton
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationsAdapter: NotificationAdapter
    private val notificationsList = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize UI components
        rvNotifications = findViewById(R.id.rvNotifications)
        tvNoNotifications = findViewById(R.id.tvNoNotifications)
        backButton = findViewById(R.id.back_button)

        // Set up RecyclerView
        notificationsAdapter = NotificationAdapter(notificationsList) { notification ->
            handleNotificationClick(notification)
        }
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = notificationsAdapter

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Load notifications
        loadNotifications()
    }

    private fun loadNotifications() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e("ClientNotification", "No user is logged in")
            return
        }

        val notificationsRef = database.child("notifications").child(currentUserId)
        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notificationsList.clear()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    tvNoNotifications.visibility = View.VISIBLE
                    rvNotifications.visibility = View.GONE
                    return
                }

                tvNoNotifications.visibility = View.GONE
                rvNotifications.visibility = View.VISIBLE

                for (notificationSnapshot in snapshot.children) {
                    val notificationId = notificationSnapshot.key ?: continue
                    val senderId = notificationSnapshot.child("senderId").getValue(String::class.java) ?: continue
                    val message = notificationSnapshot.child("message").getValue(String::class.java) ?: "New message"
                    val timestamp = notificationSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val type = notificationSnapshot.child("type").getValue(String::class.java) ?: "message"
                    val isRead = notificationSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                    val conversationId = notificationSnapshot.child("conversationId").getValue(String::class.java)

                    // Get sender name
                    fetchSenderName(senderId) { senderName ->
                        val notification = NotificationItem(
                            id = notificationId,
                            senderId = senderId,
                            senderName = senderName,
                            message = message,
                            timestamp = timestamp,
                            type = type,
                            isRead = isRead,
                            conversationId = conversationId
                        )

                        // Add to the list and sort by timestamp (newest first)
                        notificationsList.add(notification)
                        notificationsList.sortByDescending { it.timestamp }
                        notificationsAdapter.notifyDataSetChanged()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ClientNotification", "Error loading notifications: ${error.message}")
            }
        })
    }

    private fun fetchSenderName(senderId: String, callback: (String) -> Unit) {
        // First check in secretaries
        database.child("secretaries").child(senderId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                callback(name)
            } else {
                // If not a secretary, check in lawyers
                database.child("lawyers").child(senderId).get().addOnSuccessListener { lawyerSnapshot ->
                    if (lawyerSnapshot.exists()) {
                        val firstName = lawyerSnapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = lawyerSnapshot.child("lastName").getValue(String::class.java) ?: ""
                        callback("$firstName $lastName".trim().ifEmpty { "Unknown Lawyer" })
                    } else {
                        // If not a lawyer, check in users
                        database.child("Users").child(senderId).get().addOnSuccessListener { userSnapshot ->
                            if (userSnapshot.exists()) {
                                val firstName = userSnapshot.child("firstName").getValue(String::class.java) ?: ""
                                val lastName = userSnapshot.child("lastName").getValue(String::class.java) ?: ""
                                callback("$firstName $lastName".trim().ifEmpty { "Unknown User" })
                            } else {
                                callback("Unknown Sender")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleNotificationClick(notification: NotificationItem) {
        // Mark notification as read
        database.child("notifications").child(auth.currentUser?.uid ?: return)
            .child(notification.id).child("isRead").setValue(true)

        // Open chat activity if it's a message notification
        if (notification.type == "message" && notification.conversationId != null) {
            val intent = Intent(this, ChatActivity::class.java)

            // Determine if the sender is a secretary
            database.child("secretaries").child(notification.senderId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        // If sender is a secretary
                        intent.putExtra("SECRETARY_ID", notification.senderId)
                    } else {
                        // Check if sender is a client
                        database.child("Users").child(notification.senderId).get()
                            .addOnSuccessListener { userSnapshot ->
                                if (userSnapshot.exists()) {
                                    intent.putExtra("CLIENT_ID", notification.senderId)
                                }
                            }
                    }
                    startActivity(intent)
                }
        }
    }
}
