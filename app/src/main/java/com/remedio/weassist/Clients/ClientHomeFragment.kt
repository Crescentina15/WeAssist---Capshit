package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Miscellaneous.ChatbotActivity
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.R

class ClientHomeFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var welcomeMessageTextView: TextView
    private lateinit var specializationsLayout: GridLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_client_home, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers") // Change to your lawyers node

        // Initialize UI elements
        welcomeMessageTextView = view.findViewById(R.id.welcome_message)
        specializationsLayout = view.findViewById(R.id.specializations_layout)
        val searchButton: Button = view.findViewById(R.id.search_button)
        val notificationButton: ImageButton = view.findViewById(R.id.notification_icon) // Notifications button

        // Setup notification badge
        setupNotificationBadge(notificationButton)

        // Fetch user's first name from Firebase
        auth.currentUser?.let { fetchUserFirstName(it.uid) } ?: run {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        // Click listener for chatbot
        searchButton.setOnClickListener {
            val intent = Intent(requireContext(), ChatbotActivity::class.java)
            startActivity(intent)
        }

        // Click listener for notifications
        notificationButton.setOnClickListener {
            val intent = Intent(requireContext(), ClientNotificationActivity::class.java)
            startActivity(intent)
        }

        // Fetch specializations and dynamically create buttons
        fetchSpecializations()

        return view
    }

    private fun setupNotificationBadge(notificationButton: ImageButton) {
        val badge = LayoutInflater.from(context).inflate(R.layout.notification_badge, null)
        val tvBadgeCount = badge.findViewById<TextView>(R.id.tvBadgeCount)

        // Add the badge to the notification button
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END or Gravity.TOP
        params.setMargins(0, 0, 0, 0)

        // Get the parent of the notification button (should be a FrameLayout or similar)
        val buttonParent = notificationButton.parent as ViewGroup
        val buttonIndex = buttonParent.indexOfChild(notificationButton)

        // Create a new FrameLayout to hold the button and the badge
        val frameLayout = FrameLayout(requireContext())

        // Remove button from its parent
        buttonParent.removeView(notificationButton)

        // Add button to FrameLayout
        frameLayout.addView(notificationButton)

        // Add badge to FrameLayout
        frameLayout.addView(badge, params)

        // Add FrameLayout back to the original parent at the same position
        buttonParent.addView(frameLayout, buttonIndex)

        // Initially hide the badge
        badge.visibility = View.GONE

        // Listen for notification count changes
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Listen to notifications
        val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId)
        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0

                for (notificationSnapshot in snapshot.children) {
                    val isRead = notificationSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                    if (!isRead) {
                        unreadCount++
                    }
                }

                // Update badge visibility and count
                if (unreadCount > 0) {
                    badge.visibility = View.VISIBLE
                    tvBadgeCount.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                } else {
                    badge.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ClientHomeFragment", "Error loading notification count: ${error.message}")
            }
        })
    }

    private fun fetchUserFirstName(userId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java)
                    if (!firstName.isNullOrEmpty()) {
                        welcomeMessageTextView.text = "Welcome!\n$firstName"
                    } else {
                        Log.e("Firebase", "First name is null or empty")
                        welcomeMessageTextView.text = "Welcome!"
                    }
                } else {
                    Log.e("Firebase", "User data not found")
                    welcomeMessageTextView.text = "Welcome!"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
                welcomeMessageTextView.text = "Welcome!"
            }
        })
    }

    private fun fetchSpecializations() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val specializations = mutableSetOf<String>() // Use a set to avoid duplicates

                for (lawyerSnapshot in snapshot.children) {
                    val specialization = lawyerSnapshot.child("specialization").getValue(String::class.java)
                    if (!specialization.isNullOrEmpty()) {
                        specializations.add(specialization)
                    }
                }

                // Log specializations for debugging
                Log.d("Specializations", specializations.toString())

                // Dynamically create buttons for each specialization
                createSpecializationButtons(specializations.toList())
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to fetch specializations: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createSpecializationButtons(specializations: List<String>) {
        val context = context ?: return  // ✅ Ensure context is not null
        val resources = resources ?: return  // ✅ Ensure resources are available
        val container = view?.findViewById<GridLayout>(R.id.specializations_layout) ?: return  // ✅ Ensure layout exists

        container.removeAllViews() // Clear existing buttons

        for (specialization in specializations) {
            val button = Button(context).apply {
                text = specialization
                layoutParams = GridLayout.LayoutParams().apply {
                    width = resources.getDimensionPixelSize(R.dimen.button_width)
                    height = resources.getDimensionPixelSize(R.dimen.button_height)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    openLawyersList(specialization)
                }
                // Apply styling
                backgroundTintList = context.getColorStateList(R.color.purple_500)
                setTextColor(context.getColor(android.R.color.white))
                textSize = 16f
                setPadding(16, 8, 16, 8)
            }

            container.addView(button)  // ✅ Ensure container is not null
        }
    }

    private fun openLawyersList(specialization: String) {
        val intent = Intent(requireContext(), LawyersListActivity::class.java)
        intent.putExtra("SPECIALIZATION", specialization) // Pass specialization
        startActivity(intent)
    }
}