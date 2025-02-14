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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.ChatbotActivity
import com.remedio.weassist.Lawyer.LawyersListActivity
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
        val notaryLawyerButton: Button = view.findViewById(R.id.button_notary_lawyer) // Assuming button ID

        // Fetch user's first name from Firebase
        auth.currentUser?.let { fetchUserFirstName(it.uid) } ?: run {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        // Click listener for chatbot
        searchButton.setOnClickListener {
            val intent = Intent(requireContext(), ChatbotActivity::class.java)
            startActivity(intent)
        }

        // Click listener for Notary Lawyer
        notaryLawyerButton.setOnClickListener {
            openLawyersList("Notary Public")
        }

        return view
    }

    private fun fetchUserFirstName(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: "User"
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

    private fun openLawyersList(specialization: String) {
        val intent = Intent(requireContext(), LawyersListActivity::class.java)
        intent.putExtra("SPECIALIZATION", specialization) // Pass specialization
        startActivity(intent)
    }
}
