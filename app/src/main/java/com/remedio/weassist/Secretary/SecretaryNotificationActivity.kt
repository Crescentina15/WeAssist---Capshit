package com.remedio.weassist.Secretary

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
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

class SecretaryNotificationActivity : AppCompatActivity() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var tvNoNotifications: TextView
    private lateinit var backButton: ImageButton
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationsAdapter: NotificationAdapter
    private val notificationsList = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_notification)

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

        // Set up swipe-to-delete functionality
        setupSwipeToDelete()

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Load notifications
        loadNotifications()
    }

    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            private val deleteBackground = ColorDrawable(Color.RED)
            private val deleteIcon = ContextCompat.getDrawable(
                this@SecretaryNotificationActivity,
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
                val deletedNotification = notificationsList[position]

                // Remove from UI
                notificationsList.removeAt(position)
                notificationsAdapter.notifyItemRemoved(position)

                // Remove from Firebase
                removeNotificationFromDatabase(deletedNotification.id)

                // Show undo option
                Snackbar.make(
                    rvNotifications,
                    "Notification removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Restore notification if user clicks UNDO
                    notificationsList.add(position, deletedNotification)
                    notificationsAdapter.notifyItemInserted(position)
                    restoreNotificationToDatabase(deletedNotification)

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
        itemTouchHelper.attachToRecyclerView(rvNotifications)
    }

    private fun removeNotificationFromDatabase(notificationId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        database.child("notifications").child(currentUserId).child(notificationId).removeValue()
            .addOnFailureListener { e ->
                Log.e("SecretaryNotification", "Error deleting notification: ${e.message}")
            }
    }

    private fun restoreNotificationToDatabase(notification: NotificationItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        val notificationRef = database.child("notifications").child(currentUserId).child(notification.id)

        val notificationMap = hashMapOf(
            "senderId" to notification.senderId,
            "message" to notification.message,
            "timestamp" to notification.timestamp,
            "type" to notification.type,
            "isRead" to notification.isRead
        )

        notification.conversationId?.let {
            notificationMap["conversationId"] = it
        }

        notificationRef.setValue(notificationMap)
            .addOnFailureListener { e ->
                Log.e("SecretaryNotification", "Error restoring notification: ${e.message}")
            }
    }

    private fun updateEmptyState() {
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
            Log.e("SecretaryNotification", "No user is logged in")
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

                    // Get additional data specific to notification types
                    val additionalData = mutableMapOf<String, String>()

                    // Store appointment ID if exists
                    notificationSnapshot.child("appointmentId").getValue(String::class.java)?.let {
                        additionalData["appointmentId"] = it
                    }

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
                Log.e("SecretaryNotification", "Error loading notifications: ${error.message}")
            }
        })
    }

    private fun fetchSenderName(senderId: String, callback: (String) -> Unit) {
        // First check in lawyers
        database.child("lawyers").child(senderId).get().addOnSuccessListener { lawyerSnapshot ->
            if (lawyerSnapshot.exists()) {
                val name = lawyerSnapshot.child("name").getValue(String::class.java)
                    ?: "${lawyerSnapshot.child("firstName").getValue(String::class.java) ?: ""} ${lawyerSnapshot.child("lastName").getValue(String::class.java) ?: ""}"
                callback(name.trim().ifEmpty { "Unknown Lawyer" })
            } else {
                // If not a lawyer, check in clients
                database.child("Users").child(senderId).get().addOnSuccessListener { userSnapshot ->
                    if (userSnapshot.exists()) {
                        val firstName = userSnapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = userSnapshot.child("lastName").getValue(String::class.java) ?: ""
                        callback("$firstName $lastName".trim().ifEmpty { "Unknown Client" })
                    } else {
                        // If not a client, check in secretaries (though less likely)
                        database.child("secretaries").child(senderId).get().addOnSuccessListener { secretarySnapshot ->
                            if (secretarySnapshot.exists()) {
                                val name = secretarySnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                                callback(name)
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

        when (notification.type) {
            "message" -> {
                if (notification.conversationId != null) {
                    val intent = Intent(this, ChatActivity::class.java)

                    // Check if sender is a lawyer
                    database.child("lawyers").child(notification.senderId).get()
                        .addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                intent.putExtra("LAWYER_ID", notification.senderId)
                            } else {
                                // Check if sender is a client
                                database.child("Users").child(notification.senderId).get()
                                    .addOnSuccessListener { userSnapshot ->
                                        if (userSnapshot.exists()) {
                                            intent.putExtra("CLIENT_ID", notification.senderId)
                                        }
                                    }
                            }
                            intent.putExtra("CONVERSATION_ID", notification.conversationId)
                            startActivity(intent)
                        }
                }
            }
            "appointment" -> {
                database.child("notifications").child(auth.currentUser?.uid ?: return)
                    .child(notification.id).child("appointmentId").get()
                    .addOnSuccessListener { snapshot ->
                        val appointmentId = snapshot.getValue(String::class.java)
                        if (appointmentId != null) {
                            // Fixed: Use SecretaryAppointmentActivity instead of SecretaryAppointmentFragment
                            val intent = Intent(this, SecretaryAppointmentActivity::class.java)
                            intent.putExtra("APPOINTMENT_ID", appointmentId)
                            startActivity(intent)
                        }
                    }
            }
            "system" -> {
                // Handle system notifications - these typically don't navigate anywhere
                Log.d("SecretaryNotification", "System notification clicked: ${notification.message}")
            }
            else -> {
                // Handle other notification types
                Log.d("SecretaryNotification", "Unknown notification type clicked: ${notification.type}")
            }
        }
    }
}