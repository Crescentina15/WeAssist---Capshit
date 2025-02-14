package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.AddAvailabilityActivity

class LawyersListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lawyerAdapter: LawyerAdapter
    private lateinit var databaseReference: DatabaseReference
    private var lawyerList = ArrayList<Lawyer>()
    private var fromManageAvailability: Boolean = false // Flag to track navigation source

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_list)

        val specialization = intent.getStringExtra("SPECIALIZATION")
        val lawFirm = intent.getStringExtra("LAW_FIRM")

        // Check if this activity was accessed via the Manage Availability button
        fromManageAvailability = intent.getBooleanExtra("FROM_MANAGE_AVAILABILITY", false)

        recyclerView = findViewById(R.id.lawyerlist)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers")

        // Load lawyers from Firebase
        loadLawyers(specialization, lawFirm)
    }

    private fun loadLawyers(specialization: String?, lawFirm: String?) {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lawyerList.clear()
                for (lawyerSnapshot in snapshot.children) {
                    val lawyer = lawyerSnapshot.getValue(Lawyer::class.java)
                    if (lawyer != null) {
                        if (specialization != null && lawyer.specialization == specialization) {
                            lawyerList.add(lawyer)
                        } else if (lawFirm != null && lawyer.lawFirm == lawFirm) {
                            lawyerList.add(lawyer)
                        }
                    }
                }

                // Set adapter with click event
                lawyerAdapter = LawyerAdapter(lawyerList) { selectedLawyer ->
                    if (fromManageAvailability) { // Ensure Manage Availability was clicked
                        val intent = Intent(this@LawyersListActivity, AddAvailabilityActivity::class.java)
                        intent.putExtra("LAWYER_ID", selectedLawyer.id)
                        intent.putExtra("LAWYER_NAME", selectedLawyer.name)
                        intent.putExtra("LAW_FIRM", selectedLawyer.lawFirm)
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this@LawyersListActivity,
                            "You must click 'Manage Availability' first!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                recyclerView.adapter = lawyerAdapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load lawyers.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
