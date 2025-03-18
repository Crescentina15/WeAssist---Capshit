package com.remedio.weassist.Clients

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

        // Setup swipe to delete
        setupSwipeToDelete()

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Load notifications
        loadNotifications()
    }

    private fun setupSwipeToDelete() {
        // Create a callback for the swipe action
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(
            0, // Drag direction flags (none in this case)
            ItemTouchHelper.LEFT // Swipe direction flags (left to delete)
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // Not handling drag & drop, so return false
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = notificationsList[position]

                // Remove from database
                removeNotification(deletedItem)

                // Remove from UI list
                notificationsList.removeAt(position)
                notificationsAdapter.notifyItemRemoved(position)

                // Show empty view if needed
                checkForEmptyList()

                // Show a snackbar with undo option
                Snackbar.make(
                    rvNotifications,
                    "Notification removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Restore the item in the database
                    restoreNotification(deletedItem)

                    // Restore the item in the UI
                    notificationsList.add(position, deletedItem)
                    notificationsAdapter.notifyItemInserted(position)

                    // Update empty view visibility
                    checkForEmptyList()
                }.show()
            }

            // Optional: Add visual feedback for the swipe action
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

                // Optional: Draw a delete icon
                val deleteIcon = ContextCompat.getDrawable(
                    this@ClientNotificationActivity,
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
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(rvNotifications)
    }

    private fun removeNotification(notification: NotificationItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        database.child("notifications").child(currentUserId)
            .child(notification.id).removeValue()
            .addOnFailureListener { e ->
                Log.e("ClientNotification", "Failed to remove notification: ${e.message}")
            }
    }

    private fun restoreNotification(notification: NotificationItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        database.child("notifications").child(currentUserId)
            .child(notification.id).setValue(mapOf(
                "senderId" to notification.senderId,
                "message" to notification.message,
                "timestamp" to notification.timestamp,
                "type" to notification.type,
                "isRead" to notification.isRead,
                "conversationId" to notification.conversationId,
                "appointmentId" to notification.appointmentId // Added appointmentId
            )).addOnFailureListener { e ->
                Log.e("ClientNotification", "Failed to restore notification: ${e.message}")
            }
    }

    private fun checkForEmptyList() {
        if (notificationsList.isEmpty()) {
            tvNoNotifications.visibility = View.VISIBLE
            rvNotifications.visibility = View.GONE
        } else {
            tvNoNotifications.visibility = View.GONE
            rvNotifications.visibility = View.VISIBLE
        }
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
                    // Added this line to retrieve appointmentId
                    val appointmentId = notificationSnapshot.child("appointmentId").getValue(String::class.java)

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
                            conversationId = conversationId,
                            appointmentId = appointmentId // Added appointmentId
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
                callback("Secretary $name".trim().ifEmpty { "Unknown" })
            } else {
                // If not a secretary, check in lawyers
                database.child("lawyers").child(senderId).get().addOnSuccessListener { lawyerSnapshot ->
                    if (lawyerSnapshot.exists()) {
                        val fullName = lawyerSnapshot.child("name").getValue(String::class.java) ?: ""
                        callback("Atty. $fullName".trim().ifEmpty { "Unknown" })
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

        // Handle different notification types
        when (notification.type) {
            "message" -> {
                if (notification.conversationId != null) {
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
            "appointment_accepted" -> {
                // Navigate directly to appointment details with the ID
                val appointmentId = notification.appointmentId
                if (appointmentId != null) {
                    // Create an intent to navigate to appointment details
                    val intent = Intent(this, ClientAppointmentDetailsActivity::class.java)
                    intent.putExtra("APPOINTMENT_ID", appointmentId)
                    startActivity(intent)
                } else {
                    // Fallback if no appointment ID is available
                    Toast.makeText(this, "Appointment ID not found", Toast.LENGTH_SHORT).show()

                    // Navigate to the appointments list as a fallback
                    val intent = Intent(this, ClientAppointmentsActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }
}