package com.remedio.weassist.Lawyer

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
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

                        // Add the new fields for forwarded conversations
                        val conversationId = notificationSnapshot.child("conversationId").getValue(String::class.java)
                        val forwardingMessage = notificationSnapshot.child("forwardingMessage").getValue(String::class.java)

                        val notification = NotificationItem(
                            id = id,
                            senderId = senderId,
                            senderName = senderName,
                            message = message,
                            timestamp = timestamp,
                            type = type,
                            isRead = isRead,
                            appointmentId = appointmentId,
                            conversationId = conversationId,
                            forwardingMessage = forwardingMessage
                        )

                        notificationsList.add(notification)
                    } catch (e: Exception) {
                        Log.e("LawyerNotification", "Error parsing notification: ${e.message}")
                    }
                }

                // Sort notifications by timestamp (newest first)
                notificationsList.sortByDescending { it.timestamp }

                // Update the notifications list
                notifications.clear()
                notifications.addAll(notificationsList)

                // Update UI based on notifications
                checkForEmptyList()

                // Update your NotificationAdapter click handler in the fetchNotifications method:
                if (notifications.isNotEmpty()) {
                    // Initialize adapter with the notification click handler
                    // In LawyerNotification.kt, update your click handler to navigate to LawyerFrontPage instead

                    notificationAdapter = NotificationAdapter(notifications) { notification ->
                        // Mark notification as read when clicked
                        markNotificationAsRead(lawyerId, notification.id)

                        when (notification.type) {
                            "NEW_CASE_ASSIGNED" -> {
                                notification.conversationId?.let { convId ->
                                    // Navigate to LawyerFrontPage and show the message fragment
                                    val intent = Intent(this@LawyerNotification, LawyersDashboardActivity::class.java)

                                    // Pass the conversation ID to be used by the fragment
                                    intent.putExtra("CONVERSATION_ID", convId)

                                    // Add a flag to indicate we want to show the message fragment specifically
                                    intent.putExtra("SHOW_MESSAGE_FRAGMENT", true)

                                    // If you need to extract client ID from conversation ID
                                    val parts = convId.split("_")
                                    if (parts.size >= 3) {
                                        // Assuming format is "conversation_userId1_userId2"
                                        val userId1 = parts[1]
                                        val userId2 = parts[2]
                                        // Figure out which is the client ID (not the lawyer ID)
                                        val clientId = if (userId1 == lawyerId) userId2 else userId1
                                        intent.putExtra("CLIENT_ID", clientId)
                                    }

                                    // Start the activity
                                    startActivity(intent)

                                    // Optionally finish the current activity
                                    finish()
                                } ?: run {
                                    Toast.makeText(this@LawyerNotification,
                                        "Cannot open conversation, missing ID", Toast.LENGTH_SHORT).show()
                                }
                            }
                            "appointment_accepted" -> {
                                // Handle appointment acceptance notification
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
                    // Setup swipe-to-delete functionality
                    setupSwipeToDelete(lawyerId)
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

    private fun checkForEmptyList() {
        if (notifications.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupSwipeToDelete(userId: String) {
        // Create a callback for swipe actions - only LEFT swipe allowed
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // We're not using drag & drop functionality
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition

                // Make sure the position is valid
                if (position >= 0 && position < notifications.size) {
                    val deletedItem = notifications[position]

                    // Remove from database
                    removeNotification(userId, deletedItem.id)

                    // Remove from UI list
                    notifications.removeAt(position)
                    notificationAdapter.notifyItemRemoved(position)

                    // Show empty view if needed
                    checkForEmptyList()

                    // Show a snackbar with undo option
                    Snackbar.make(
                        recyclerView,
                        "Notification removed",
                        Snackbar.LENGTH_LONG
                    ).setAction("UNDO") {
                        // Restore the item in the database
                        restoreNotification(userId, deletedItem)

                        // Restore the item in the UI
                        notifications.add(position, deletedItem)
                        notificationAdapter.notifyItemInserted(position)

                        // Update empty view visibility
                        checkForEmptyList()
                    }.show()
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.RED)

                // Draw the red background
                background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                background.draw(c)

                // Draw a delete icon
                val deleteIcon = ContextCompat.getDrawable(
                    this@LawyerNotification,
                    android.R.drawable.ic_menu_delete
                )

                deleteIcon?.let {
                    val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + it.intrinsicHeight
                    val iconRight = itemView.right - iconMargin
                    val iconLeft = iconRight - it.intrinsicWidth

                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.draw(c)
                }

                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

        // Attach the swipe callback to the RecyclerView
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(recyclerView)
    }

    private fun removeNotification(userId: String, notificationId: String) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications")
            .child(userId).child(notificationId)

        // Save the notification data temporarily (for potential restoration)
        notificationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Store the notification data for potential restoration
                val notificationData = snapshot.value

                // Remove the notification from the database
                notificationRef.removeValue()
                    .addOnFailureListener { e ->
                        Log.e("Notification", "Failed to delete notification: ${e.message}")
                        Toast.makeText(this@LawyerNotification, "Failed to remove notification", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Notification", "Failed to get notification data: ${error.message}")
            }
        })
    }

    private fun restoreNotification(userId: String, notification: NotificationItem) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications")
            .child(userId).child(notification.id)

        // Create a map of notification data
        val notificationMap = HashMap<String, Any?>()
        notificationMap["senderId"] = notification.senderId
        notificationMap["senderName"] = notification.senderName
        notificationMap["message"] = notification.message
        notificationMap["timestamp"] = notification.timestamp
        notificationMap["type"] = notification.type
        notificationMap["isRead"] = notification.isRead
        notification.appointmentId?.let { notificationMap["appointmentId"] = it }
        notification.conversationId?.let { notificationMap["conversationId"] = it }
        notification.forwardingMessage?.let { notificationMap["forwardingMessage"] = it }

        // Restore the notification in the database
        notificationRef.setValue(notificationMap)
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to restore notification: ${e.message}")
                Toast.makeText(this@LawyerNotification, "Failed to restore notification", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markNotificationAsRead(userId: String, notificationId: String) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications")
            .child(userId).child(notificationId)

        // Update the isRead field to true
        notificationRef.child("isRead").setValue(true)
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to mark notification as read: ${e.message}")
            }
    }
}