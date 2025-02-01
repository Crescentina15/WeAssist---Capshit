package com.remedio.weassist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

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

        // Fetch user's first name from Firebase
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserFirstName(currentUser.uid)
        } else {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
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
