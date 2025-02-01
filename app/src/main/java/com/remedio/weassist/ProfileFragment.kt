package com.remedio.weassist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var editProfileButton: LinearLayout
    private lateinit var securityButton: LinearLayout
    private lateinit var privacyButton: LinearLayout
    private lateinit var reportProblemButton: LinearLayout
    private lateinit var logoutButton: LinearLayout

    private val PREFS_NAME = "LoginPrefs"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Initialize UI elements
        usernameTextView = view.findViewById(R.id.headername)
        emailTextView = view.findViewById(R.id.headerprofile)
        editProfileButton = view.findViewById(R.id.edit_profile)
        securityButton = view.findViewById(R.id.security)
        privacyButton = view.findViewById(R.id.privacy)
        reportProblemButton = view.findViewById(R.id.report_problem)
        logoutButton = view.findViewById(R.id.log_out)

        // Fetch user data using auth.uid
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserData(currentUser.uid)
        } else {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        // Edit Profile button click
        editProfileButton.setOnClickListener {
            startActivity(Intent(activity, EditProfileActivity::class.java))
        }

        // Security button click
        securityButton.setOnClickListener {
            startActivity(Intent(activity, SecurityActivity::class.java))
        }

        // Privacy button click
        privacyButton.setOnClickListener {
            startActivity(Intent(activity, PrivacyActivity::class.java))
        }

        // Report Problem button click
        reportProblemButton.setOnClickListener {
            startActivity(Intent(activity, ReportActivity::class.java))
        }

        // Logout button click
        logoutButton.setOnClickListener {
            logoutUser()
        }

        return view
    }

    private fun fetchUserData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: "N/A"
                    val email = snapshot.child("email").getValue(String::class.java) ?: "N/A"

                    // Update UI
                    usernameTextView.text = firstName
                    emailTextView.text = email
                } else {
                    Toast.makeText(context, "User data not found!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun logoutUser() {
        // Clear stored login credentials
        val sharedPreferences = activity?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.clear()?.apply()

        auth.signOut()
        Toast.makeText(context, "You have been logged out", Toast.LENGTH_SHORT).show()

        // Redirect to Login screen
        startActivity(Intent(activity, Login::class.java))
        activity?.finish()
    }
}
