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
import com.remedio.weassist.Models.NotificationItem
import com.remedio.weassist.Models.NotificationAdapter
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

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

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

        // Initialize adapter with the notification click handler
        notificationAdapter = NotificationAdapter(notifications) { notification ->
            handleNotificationClick(notification)
        }
        recyclerView.adapter = notificationAdapter

        // Setup swipe-to-delete functionality
        setupSwipeToDelete()

        // Fetch notifications
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            fetchNotifications(currentUserId)
        } else {
            Log.e("LawyerNotification", "Lawyer ID is null")
            Toast.makeText(this, "Lawyer not logged in", Toast.LENGTH_SHORT).show()
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            private val deleteBackground = ColorDrawable(Color.RED)
            private val deleteIcon = ContextCompat.getDrawable(
                this@LawyerNotification,
                android.R.drawable.ic_menu_delete
            )

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // We don't want drag & drop
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = notifications[position]

                // Remove from UI
                notifications.removeAt(position)
                notificationAdapter.notifyItemRemoved(position)

                // Remove from Firebase
                removeNotificationFromDatabase(deletedItem.id)

                // Show undo option
                Snackbar.make(
                    recyclerView,
                    "Notification removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Restore notification if user clicks UNDO
                    notifications.add(position, deletedItem)
                    notificationAdapter.notifyItemInserted(position)
                    restoreNotificationToDatabase(deletedItem)

                    // Show empty state if needed
                    updateEmptyState()
                }.show()

                // Show empty state if needed
                updateEmptyState()
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
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2

                // Draw red background
                deleteBackground.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                deleteBackground.draw(c)

                // Draw delete icon
                deleteIcon?.let {
                    val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                    val iconTop = itemView.top + iconMargin
                    val iconRight = itemView.right - iconMargin
                    val iconBottom = iconTop + it.intrinsicHeight

                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun updateEmptyState() {
        if (notifications.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun fetchNotifications(lawyerId: String) {
        val notificationsRef = database.child("notifications").child(lawyerId)
        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notifications.clear()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    return
                }

                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                for (notificationSnapshot in snapshot.children) {
                    try {
                        val notificationId = notificationSnapshot.key ?: continue
                        val senderId = notificationSnapshot.child("senderId").getValue(String::class.java) ?: continue
                        val message = notificationSnapshot.child("message").getValue(String::class.java) ?: "New message"
                        val timestamp = notificationSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        val type = notificationSnapshot.child("type").getValue(String::class.java) ?: "message"
                        val isRead = notificationSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                        val conversationId = notificationSnapshot.child("conversationId").getValue(String::class.java)
                        val appointmentId = notificationSnapshot.child("appointmentId").getValue(String::class.java)
                        val forwardingMessage = notificationSnapshot.child("forwardingMessage").getValue(String::class.java)
                        val clientId = notificationSnapshot.child("clientId").getValue(String::class.java)

                        // Get sender name for display
                        fetchSenderName(senderId) { senderName ->
                            val notification = NotificationItem(
                                id = notificationId,
                                senderId = senderId,
                                senderName = senderName,
                                message = message,
                                timestamp = timestamp,
                                type = type,
                                isRead = isRead,
                                conversationId = conversationId,
                                appointmentId = appointmentId,
                                forwardingMessage = forwardingMessage,
                                clientId = clientId
                            )

                            // Add to the list and sort by timestamp (newest first)
                            notifications.add(notification)
                            notifications.sortByDescending { it.timestamp }
                            notificationAdapter.notifyDataSetChanged()
                        }
                    } catch (e: Exception) {
                        Log.e("LawyerNotification", "Error parsing notification: ${e.message}")
                    }
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

    private fun fetchSenderName(senderId: String, callback: (String) -> Unit) {
        // First check in secretaries
        database.child("secretaries").child(senderId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                callback("Secretary $name".trim().ifEmpty { "Unknown Secretary" })
            } else {
                // If not a secretary, check in users (clients)
                database.child("Users").child(senderId).get().addOnSuccessListener { userSnapshot ->
                    if (userSnapshot.exists()) {
                        val firstName = userSnapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = userSnapshot.child("lastName").getValue(String::class.java) ?: ""
                        callback("$firstName $lastName".trim().ifEmpty { "Unknown Client" })
                    } else {
                        // If not a client, just use the sender ID
                        callback("Unknown Sender")
                    }
                }.addOnFailureListener {
                    callback("Unknown Sender")
                }
            }
        }.addOnFailureListener {
            callback("Unknown Sender")
        }
    }

    private fun handleNotificationClick(notification: NotificationItem) {
        // Mark notification as read
        markNotificationAsRead(notification.id)

        when (notification.type) {
            "NEW_CASE_ASSIGNED" -> {
                if (notification.conversationId != null) {
                    // Extract client ID if needed
                    val clientId = notification.clientId ?: extractClientIdFromConversationId(notification.conversationId)

                    // Navigate to LawyersDashboardActivity and show message fragment
                    val intent = Intent(this@LawyerNotification, LawyersDashboardActivity::class.java)
                    intent.putExtra("CONVERSATION_ID", notification.conversationId)
                    intent.putExtra("CLIENT_ID", clientId)
                    intent.putExtra("SHOW_MESSAGE_FRAGMENT", true)

                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@LawyerNotification,
                        "Cannot open conversation: Missing conversation ID",
                        Toast.LENGTH_SHORT).show()
                }
            }
            "message" -> {
                // Regular message notification
                if (notification.conversationId != null) {
                    val intent = Intent(this, ChatActivity::class.java)

                    // Extract client ID if needed
                    val clientId = notification.clientId ?: extractClientIdFromConversationId(notification.conversationId)

                    intent.putExtra("CONVERSATION_ID", notification.conversationId)
                    intent.putExtra("CLIENT_ID", clientId)
                    intent.putExtra("USER_TYPE", "lawyer")

                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Cannot open conversation: Missing ID", Toast.LENGTH_SHORT).show()
                }
            }
            "appointment_accepted", "appointment_forwarded" -> {
                // Handle appointment notifications
                if (notification.appointmentId != null) {
                    // Navigate to appointment details - implement this part
                    // based on your appointment detail activity
                    Toast.makeText(this, "Opening appointment details", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Cannot open appointment: Missing ID", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // Default handling
                Toast.makeText(this, "Notification acknowledged", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractClientIdFromConversationId(conversationId: String): String? {
        // Assuming conversation ID format: "conversation_user1_user2"
        val parts = conversationId.split("_")
        if (parts.size >= 3) {
            val userId1 = parts[1]
            val userId2 = parts[2]
            val currentUserId = auth.currentUser?.uid

            // Return the ID that isn't the current user
            return if (userId1 == currentUserId) userId2 else userId1
        }
        return null
    }

    private fun markNotificationAsRead(notificationId: String) {
        val userId = auth.currentUser?.uid ?: return
        database.child("notifications").child(userId)
            .child(notificationId).child("isRead").setValue(true)
            .addOnFailureListener { e ->
                Log.e("LawyerNotification", "Failed to mark notification as read: ${e.message}")
            }
    }

    private fun removeNotificationFromDatabase(notificationId: String) {
        val userId = auth.currentUser?.uid ?: return
        database.child("notifications").child(userId).child(notificationId).removeValue()
            .addOnFailureListener { e ->
                Log.e("LawyerNotification", "Error deleting notification: ${e.message}")
            }
    }

    private fun restoreNotificationToDatabase(notification: NotificationItem) {
        val userId = auth.currentUser?.uid ?: return
        val notificationRef = database.child("notifications").child(userId).child(notification.id)

        val notificationMap = HashMap<String, Any?>()
        notificationMap["senderId"] = notification.senderId
        notificationMap["senderName"] = notification.senderName
        notificationMap["message"] = notification.message
        notificationMap["timestamp"] = notification.timestamp
        notificationMap["type"] = notification.type
        notificationMap["isRead"] = notification.isRead
        notification.conversationId?.let { notificationMap["conversationId"] = it }
        notification.appointmentId?.let { notificationMap["appointmentId"] = it }
        notification.forwardingMessage?.let { notificationMap["forwardingMessage"] = it }
        notification.clientId?.let { notificationMap["clientId"] = it }

        notificationRef.setValue(notificationMap)
            .addOnFailureListener { e ->
                Log.e("LawyerNotification", "Failed to restore notification: ${e.message}")
            }
    }
}