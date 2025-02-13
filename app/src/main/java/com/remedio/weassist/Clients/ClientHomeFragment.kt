package com.remedio.weassist.Clients


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.ChatbotActivity
import com.remedio.weassist.R

class ClientHomeFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var welcomeMessageTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home_client, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Initialize UI elements
        welcomeMessageTextView = view.findViewById(R.id.welcome_message)
        val searchButton: Button = view.findViewById(R.id.search_button)
        val profileSection: View = view.findViewById(R.id.profile_section)
        val profileImage: View = view.findViewById(R.id.profile_image)
        val notificationIcon: View = view.findViewById(R.id.notification_icon)
        val specializationLayout: ViewGroup = view.findViewById(R.id.specializations_layout)

        // Fetch user's first name from Firebase
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserFirstName(currentUser.uid)
        } else {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        // Click listeners for different elements
        searchButton.setOnClickListener {
            val intent = Intent(requireContext(), ChatbotActivity::class.java)
            startActivity(intent)
        }

        profileSection.setOnClickListener {
            Toast.makeText(requireContext(), "Profile Section Clicked", Toast.LENGTH_SHORT).show()
        }

        profileImage.setOnClickListener {
            Toast.makeText(requireContext(), "Profile Image Clicked", Toast.LENGTH_SHORT).show()
        }

        notificationIcon.setOnClickListener {
            Toast.makeText(requireContext(), "Notifications Clicked", Toast.LENGTH_SHORT).show()
        }

        // Make all specialization buttons clickable
        for (i in 0 until specializationLayout.childCount) {
            val child = specializationLayout.getChildAt(i)
            child.setOnClickListener {
                Toast.makeText(requireContext(), "${(child as Button).text} Clicked", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }


    private fun fetchUserFirstName(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: "User"

                    // Set Welcome Message
                    welcomeMessageTextView.text = "Welcome, $firstName!"
                } else {
                    Toast.makeText(context, "User data not found!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
