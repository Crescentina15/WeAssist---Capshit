package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.R

class SecretaryDashboardFragment : Fragment() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var secretaryNameTextView: TextView
    private lateinit var secretaryFirmTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_dashboard, container, false)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference("secretaries")

        secretaryNameTextView = view.findViewById(R.id.secretary_fname)
        secretaryFirmTextView = view.findViewById(R.id.secretary_firm)

        loadSecretaryDetails()

        val manageAvailabilityButton = view.findViewById<ImageButton>(R.id.manage_availability_button)
        val addBackgroundButton = view.findViewById<ImageButton>(R.id.add_background_button)
        val addBalanceButton = view.findViewById<ImageButton>(R.id.add_balance_button)

        manageAvailabilityButton.setOnClickListener { fetchLawFirmAndOpenLawyersList("manage_availability") }
        addBackgroundButton.setOnClickListener { fetchLawFirmAndOpenLawyersList("add_background") }
        addBalanceButton.setOnClickListener { fetchLawFirmAndOpenLawyersList("add_balance") }

        return view
    }

    private fun loadSecretaryDetails() {
        val userId = auth.currentUser?.uid ?: return

        // Fetch secretary's name
        databaseReference.child(userId).child("name").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                secretaryNameTextView.text = snapshot.value?.toString() ?: "Secretary"
            }

            override fun onCancelled(error: DatabaseError) {
                secretaryNameTextView.text = "Error loading name"
            }
        })

        // Fetch law firm's name
        databaseReference.child(userId).child("lawFirm").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                secretaryFirmTextView.text = snapshot.value?.toString() ?: "No Law Firm Assigned"
            }

            override fun onCancelled(error: DatabaseError) {
                secretaryFirmTextView.text = "Error loading law firm"
            }
        })
    }

    private fun fetchLawFirmAndOpenLawyersList(action: String) {
        val userId = auth.currentUser?.uid ?: return

        databaseReference.child(userId).child("lawFirm").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawFirm = snapshot.value.toString()
                    val intent = Intent(requireContext(), LawyersListActivity::class.java).apply {
                        putExtra("LAW_FIRM", lawFirm)
                        when (action) {
                            "manage_availability" -> putExtra("FROM_MANAGE_AVAILABILITY", true)
                            "add_background" -> putExtra("FROM_ADD_BACKGROUND", true)
                            "add_balance" -> putExtra("FROM_ADD_BALANCE", true)
                        }
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Law firm not found.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error fetching law firm.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

