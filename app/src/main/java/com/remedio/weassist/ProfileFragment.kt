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


