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

import com.remedio.weassist.LoginAndRegister.Login
import com.remedio.weassist.Miscellaneous.PrivacyActivity
import com.remedio.weassist.Miscellaneous.ReportActivity
import com.remedio.weassist.Miscellaneous.SecurityActivity

class SecretaryProfileFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var nameTextView: TextView
    private lateinit var editProfileButton: LinearLayout
    private lateinit var securityButton: LinearLayout
    private lateinit var privacyButton: LinearLayout
    private lateinit var logoutButton: LinearLayout

    private val PREFS_NAME = "LoginPrefs"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_profile, container, false)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("secretaries")

        // Initialize UI elements
        nameTextView = view.findViewById(R.id.secretary_name)
        editProfileButton = view.findViewById(R.id.secretary_edit_profile)
        securityButton = view.findViewById(R.id.secretary_security)
        privacyButton = view.findViewById(R.id.secretary_privacy)
        logoutButton = view.findViewById(R.id.secretary_log_out)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchSecretaryData(currentUser.uid)
        } else {
            showToast("User not logged in")
        }

        editProfileButton.setOnClickListener {
            openActivity(EditSecretaryProfileActivity::class.java)
        }

        securityButton.setOnClickListener { openActivity(SecurityActivity::class.java) }
        privacyButton.setOnClickListener { openActivity(PrivacyActivity::class.java) }
        logoutButton.setOnClickListener { logoutUser() }
    }

    private fun fetchSecretaryData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "N/A"
                    nameTextView.text = name
                } else {
                    showToast("Secretary data not found!")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) showToast("Database error: ${error.message}")
            }
        })
    }

    private fun logoutUser() {
        if (!isAdded) return

        val sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        auth.signOut()
        showToast("You have been logged out")

        startActivity(Intent(requireActivity(), Login::class.java))
        requireActivity().finish()
    }

    private fun openActivity(activityClass: Class<*>) {
        if (isAdded) {
            startActivity(Intent(requireActivity(), activityClass))
        }
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
