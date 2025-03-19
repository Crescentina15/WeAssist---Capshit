package com.remedio.weassist.Clients

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.LoginAndRegister.Login
import com.remedio.weassist.Miscellaneous.PrivacyActivity
import com.remedio.weassist.Miscellaneous.ReportActivity
import com.remedio.weassist.Miscellaneous.SecurityActivity
import com.remedio.weassist.R

class ClientProfileFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var profileImageView: ImageView
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
        val view = inflater.inflate(R.layout.fragment_client_profile, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Initialize UI elements
        usernameTextView = view.findViewById(R.id.headername)
        emailTextView = view.findViewById(R.id.headerprofile)
        profileImageView = view.findViewById(R.id.profile_image)
        editProfileButton = view.findViewById(R.id.edit_profile)
        securityButton = view.findViewById(R.id.security)
        privacyButton = view.findViewById(R.id.privacy)
        reportProblemButton = view.findViewById(R.id.report_problem)
        logoutButton = view.findViewById(R.id.log_out)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserData(currentUser.uid)
        } else {
            showToast("User not logged in")
        }

        editProfileButton.setOnClickListener { openActivity(ClientEditProfileActivity::class.java) }
        securityButton.setOnClickListener { openActivity(SecurityActivity::class.java) }
        privacyButton.setOnClickListener { openActivity(PrivacyActivity::class.java) }
        reportProblemButton.setOnClickListener { openActivity(ReportActivity::class.java) }
        logoutButton.setOnClickListener { logoutUser() }
    }

    private fun fetchUserData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the Fragment is still attached and in a valid state
                if (!isAdded || isDetached || activity == null) {
                    return
                }

                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                    val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                    usernameTextView.text = "$firstName $lastName".trim()

                    // Ensure the Fragment is still in a valid state before loading the image
                    if (isAdded && !isDetached && activity != null) {
                        if (profileImageUrl.isNotEmpty()) {
                            Glide.with(requireContext()).load(profileImageUrl).into(profileImageView)
                        }
                    }
                } else {
                    showToast("User data not found!")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && !isDetached && activity != null) {
                    showToast("Database error: ${error.message}")
                }
            }
        })
    }

    private fun logoutUser() {
        if (!isAdded || isDetached || activity == null) return

        val sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        auth.signOut()
        showToast("You have been logged out")

        startActivity(Intent(requireActivity(), Login::class.java))
        requireActivity().finish()
    }

    private fun openActivity(activityClass: Class<*>) {
        if (isAdded && !isDetached && activity != null) {
            startActivity(Intent(requireActivity(), activityClass))
        }
    }

    private fun showToast(message: String) {
        if (isAdded && !isDetached && activity != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}