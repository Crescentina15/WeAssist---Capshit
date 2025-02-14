package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.R

class SecretaryDashboardFragment : Fragment() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var secretaryNameTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_dashboard, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference("secretaries")

        // Find TextView for secretary's name
        secretaryNameTextView = view.findViewById(R.id.secretary_fname)

        // Load the secretary's name
        loadSecretaryName()

        // Find ImageButton and set click listeners
        val manageButton = view.findViewById<ImageButton>(R.id.manage_availability_button)
        manageButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyersListActivity::class.java)
            startActivity(intent)
        }

        val addBackgroundButton = view.findViewById<ImageButton>(R.id.add_background_button)
        addBackgroundButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyersListActivity::class.java)
            startActivity(intent)
        }

        val addBalanceButton = view.findViewById<ImageButton>(R.id.add_balance_button)
        addBalanceButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyersListActivity::class.java)
            startActivity(intent)
        }

        return view
    }

    private fun loadSecretaryName() {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            // Fetch secretary name from Realtime Database
            databaseReference.child(userId).child("name").addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val secretaryName = snapshot.value.toString()
                        secretaryNameTextView.text = secretaryName
                    } else {
                        secretaryNameTextView.text = "Secretary"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    secretaryNameTextView.text = "Error loading name"
                }
            })
        }
    }
}
