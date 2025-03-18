package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.remedio.weassist.Miscellaneous.AddBalanceActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.AddAvailabilityActivity
import com.remedio.weassist.Secretary.AddBackgroundActivity
import com.remedio.weassist.Secretary.SetAppointmentActivity

class LawyersListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lawyerAdapter: LawyerAdapter
    private lateinit var databaseReference: DatabaseReference
    private lateinit var lawFirmAdminReference: DatabaseReference
    private var lawyerList = ArrayList<Lawyer>()

    private var fromManageAvailability = false
    private var fromAddBackgroundActivity = false
    private var fromAddBalanceActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_list)

        val specialization = intent.getStringExtra("SPECIALIZATION")
        val lawFirm = intent.getStringExtra("LAW_FIRM")

        // Retrieve intent flags
        fromManageAvailability = intent.getBooleanExtra("FROM_MANAGE_AVAILABILITY", false)
        fromAddBackgroundActivity = intent.getBooleanExtra("FROM_ADD_BACKGROUND", false)
        fromAddBalanceActivity = intent.getBooleanExtra("FROM_ADD_BALANCE", false)

        recyclerView = findViewById(R.id.lawyerlist)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers")
        lawFirmAdminReference = FirebaseDatabase.getInstance().getReference("law_firm_admin")

        // Load lawyers from Firebase with firm locations
        loadLawyers(specialization, lawFirm)

        // Handle back button click
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadLawyers(specialization: String?, lawFirm: String?) {
        // First, load all law firm admins to have their data available
        lawFirmAdminReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(adminSnapshot: DataSnapshot) {
                // Create a map of law firm names to their office addresses
                val firmLocationMap = HashMap<String, String>()

                for (adminData in adminSnapshot.children) {
                    val admin = adminData.value as? Map<String, Any> ?: continue
                    val firmName = admin["lawFirm"]?.toString() ?: continue
                    val officeAddress = admin["officeAddress"]?.toString() ?: ""

                    firmLocationMap[firmName] = officeAddress
                }

                // Now load lawyers with firm locations
                loadLawyersWithFirmLocations(specialization, lawFirm, firmLocationMap)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load firm data.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadLawyersWithFirmLocations(specialization: String?, lawFirm: String?, firmLocationMap: Map<String, String>) {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lawyerList.clear()
                for (lawyerSnapshot in snapshot.children) {
                    val lawyerId = lawyerSnapshot.key ?: continue
                    val lawyerData = lawyerSnapshot.value as? Map<String, Any> ?: continue

                    val contactData = lawyerData["contact"]
                    val contact = when (contactData) {
                        is Map<*, *> -> Contact(
                            phone = contactData["phone"]?.toString() ?: "",
                            email = contactData["email"]?.toString() ?: "",
                            address = contactData["address"]?.toString() ?: ""
                        )
                        is String -> Contact(phone = contactData)
                        else -> Contact()
                    }

                    val lawyerFirm = lawyerData["lawFirm"]?.toString() ?: ""
                    // Get the office address from the firm location map
                    val firmLocation = firmLocationMap[lawyerFirm] ?: ""

                    val lawyer = Lawyer(
                        id = lawyerId,
                        name = lawyerData["name"]?.toString() ?: "",
                        specialization = lawyerData["specialization"]?.toString() ?: "",
                        lawFirm = lawyerFirm,
                        licenseNumber = lawyerData["licenseNumber"]?.toString() ?: "",
                        experience = lawyerData["experience"]?.toString() ?: "",
                        profileImageUrl = lawyerData["profileImageUrl"]?.toString(), // Add this line
                        location = firmLocation,  // Set the firm's office address as location
                        ratings = lawyerData["ratings"]?.toString() ?: "",
                        contact = contact
                    )

                    if ((specialization == null || lawyer.specialization == specialization) &&
                        (lawFirm == null || lawyer.lawFirm == lawFirm)
                    ) {
                        lawyerList.add(lawyer)
                    }
                }

                lawyerAdapter = LawyerAdapter(lawyerList) { selectedLawyer ->
                    val context = this@LawyersListActivity

                    if (!fromManageAvailability && !fromAddBackgroundActivity && !fromAddBalanceActivity) {
                        // Launch LawyerBackgroundActivity for clients
                        val intent = Intent(context, LawyerBackgroundActivity::class.java)
                        intent.putExtra("LAWYER_ID", selectedLawyer.id)
                        startActivity(intent)
                    } else {
                        // Existing logic for other cases
                        val intent = when {
                            fromManageAvailability -> Intent(
                                context,
                                AddAvailabilityActivity::class.java
                            )

                            fromAddBackgroundActivity -> Intent(
                                context,
                                AddBackgroundActivity::class.java
                            )

                            fromAddBalanceActivity -> Intent(
                                context,
                                AddBalanceActivity::class.java
                            )

                            else -> Intent(context, SetAppointmentActivity::class.java)
                        }

                        intent.putExtra("LAWYER_ID", selectedLawyer.id)
                        intent.putExtra("LAWYER_NAME", selectedLawyer.name)
                        intent.putExtra("LAW_FIRM", selectedLawyer.lawFirm)
                        // Also pass the location from law firm admin
                        intent.putExtra("LOCATION", selectedLawyer.location)

                        startActivity(intent)
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