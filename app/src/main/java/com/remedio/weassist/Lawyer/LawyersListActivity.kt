package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.remedio.weassist.R

class LawyersListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lawyerAdapter: LawyerAdapter
    private lateinit var databaseReference: DatabaseReference
    private var lawyerList = ArrayList<Lawyer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_list)

        // Get specialization or law firm from Intent
        val specialization = intent.getStringExtra("SPECIALIZATION")
        val lawFirm = intent.getStringExtra("LAW_FIRM")

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.lawyerlist)
        recyclerView.layoutManager = LinearLayoutManager(this)
        lawyerAdapter = LawyerAdapter(lawyerList)
        recyclerView.adapter = lawyerAdapter

        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers")

        // Load lawyers based on specialization or law firm
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
                lawyerAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load lawyers.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
