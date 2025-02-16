package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
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

        // Load lawyers from Firebase
        loadLawyers(specialization, lawFirm)
    }

    private fun loadLawyers(specialization: String?, lawFirm: String?) {
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

                    val lawyer = Lawyer(
                        id = lawyerId,
                        name = lawyerData["name"]?.toString() ?: "",
                        specialization = lawyerData["specialization"]?.toString() ?: "",
                        lawFirm = lawyerData["lawFirm"]?.toString() ?: "",
                        licenseNumber = lawyerData["licenseNumber"]?.toString() ?: "",
                        experience = lawyerData["experience"]?.toString() ?: "",
                        bio = lawyerData["bio"]?.toString() ?: "",
                        contact = contact
                    )

                    if ((specialization == null || lawyer.specialization == specialization) &&
                        (lawFirm == null || lawyer.lawFirm == lawFirm)
                    ) {
                        lawyerList.add(lawyer)
                    }
                }

                lawyerAdapter = LawyerAdapter(lawyerList) { selectedLawyer ->
                    val context = this@LawyersListActivity  // Ensure correct context

                    val intent = when {
                        fromManageAvailability -> Intent(context, AddAvailabilityActivity::class.java)
                        fromAddBackgroundActivity -> Intent(context, AddBackgroundActivity::class.java)
                        fromAddBalanceActivity -> Intent(context, AddBalanceActivity::class.java)
                        else -> Intent(context, SetAppointmentActivity::class.java)
                    }

                    intent.putExtra("LAWYER_ID", selectedLawyer.id)
                    intent.putExtra("LAWYER_NAME", selectedLawyer.name)
                    intent.putExtra("LAW_FIRM", selectedLawyer.lawFirm)

                    startActivity(intent)
                }

                recyclerView.adapter = lawyerAdapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load lawyers.", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }
}
