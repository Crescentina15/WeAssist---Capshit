package com.remedio.weassist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var editProfileButton: LinearLayout
    private lateinit var securityButton: LinearLayout
    private lateinit var privacyButton: LinearLayout
    private lateinit var reportProblemButton: LinearLayout
    private lateinit var logoutButton: LinearLayout
    private val PREFS_NAME = "LoginPrefs"
    private val KEY_EMAIL = "email"
    private val KEY_USERNAME = "username"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize the TextView to display the username
        usernameTextView = view.findViewById(R.id.headername)
        emailTextView = view.findViewById(R.id.headerprofile)
        editProfileButton = view.findViewById(R.id.edit_profile)
        securityButton = view.findViewById(R.id.security)
        privacyButton = view.findViewById(R.id.privacy)
        reportProblemButton = view.findViewById(R.id.report_problem)
        logoutButton = view.findViewById(R.id.log_out)

        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Get the saved email or username from SharedPreferences
        val sharedPreferences = activity?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val emailOrUsername = sharedPreferences?.getString(KEY_EMAIL, null)
            ?: sharedPreferences?.getString(KEY_USERNAME, null)

        if (!emailOrUsername.isNullOrEmpty()) {
            // Query the database for the user using email or username
            fetchUsernameFromDatabase(emailOrUsername)
        }

        // Edit Profile button click
        editProfileButton.setOnClickListener {
            // Redirect to Edit Profile activity
            val intent = Intent(activity, EditProfileActivity::class.java)
            startActivity(intent)
        }

        // Security button click
        securityButton.setOnClickListener {
            // Redirect to Security settings
            val intent = Intent(activity, SecurityActivity::class.java)
            startActivity(intent)
        }

        // Privacy button click
        privacyButton.setOnClickListener {
            // Redirect to Privacy settings
            val intent = Intent(activity, PrivacyActivity::class.java)
            startActivity(intent)
        }

        // Report Problem button click
        reportProblemButton.setOnClickListener {
            // Redirect to Report Problem activity
            val intent = Intent(activity, ReportActivity::class.java)
            startActivity(intent)
        }

        // Logout button click
        logoutButton.setOnClickListener {
            // Log out the user from Firebase
            FirebaseAuth.getInstance().signOut()



            // Show a logout confirmation message
            Toast.makeText(context, "You have been logged out", Toast.LENGTH_SHORT).show()

            // Redirect to Login screen
            val intent = Intent(activity, Login::class.java)
            startActivity(intent)

            // Optionally, finish the current activity to prevent returning to it after logout
            activity?.finish()
        }

        return view
    }

    private fun fetchUsernameFromDatabase(emailOrUsername: String) {
        // Query the database for the email or username
        database.orderByChild("email").equalTo(emailOrUsername)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            val username = userSnapshot.child("username").value.toString()
                            // Update the username TextView with the fetched username
                            usernameTextView.text = username
                        }
                    } else {
                        // If email not found, check for username instead
                        database.orderByChild("username").equalTo(emailOrUsername)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        for (userSnapshot in snapshot.children) {
                                            val username = userSnapshot.child("username").value.toString()
                                            // Update the username TextView with the fetched username
                                            usernameTextView.text = username
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
