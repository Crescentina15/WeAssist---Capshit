package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        specializationsLayout.removeAllViews() // Clear existing buttons

        for (specialization in specializations) {
            val button = Button(context).apply {
                text = specialization // Set the button text to the specialization name
                layoutParams = GridLayout.LayoutParams().apply {
                    width = resources.getDimensionPixelSize(R.dimen.button_width) // Set fixed width
                    height = resources.getDimensionPixelSize(R.dimen.button_height) // Set fixed height
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    openLawyersList(specialization)
                }
                // Apply styling
                backgroundTintList = context?.getColorStateList(R.color.purple_500) // Set button background color
                setTextColor(context?.getColor(android.R.color.white) ?: 0) // Set text color to white
                textSize = 16f // Set text size
                setPadding(16, 8, 16, 8) // Add padding to the button text
            }

            specializationsLayout.addView(button)
        }
    }

    private fun openLawyersList(specialization: String) {
        val intent = Intent(requireContext(), LawyersListActivity::class.java)
        intent.putExtra("SPECIALIZATION", specialization) // Pass specialization
        startActivity(intent)
    }
}
