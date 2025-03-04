package com.remedio.weassist



import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ChatActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvSecretaryName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val lawyerId = intent.getStringExtra("LAWYER_ID") ?: return
        tvSecretaryName = findViewById(R.id.name_secretary)
        database = FirebaseDatabase.getInstance().reference

        // Fetch and set the secretary's name
        getSecretaryName(lawyerId)
    }

    private fun getSecretaryName(lawyerId: String) {
        val database = FirebaseDatabase.getInstance().reference
        database.child("lawyers").child(lawyerId).get().addOnSuccessListener { lawyerSnapshot ->
            if (lawyerSnapshot.exists()) {
                Log.d("ChatActivity", "Lawyer Data: ${lawyerSnapshot.value}") // Logs entire lawyer data

                // Ensure correct field name
                val secretaryId = lawyerSnapshot.child("secretaryID").value?.toString()

                if (secretaryId != null && secretaryId.isNotEmpty()) {
                    Log.d("ChatActivity", "Extracted Secretary ID: $secretaryId") // Logs secretary ID

                    database.child("secretaries").child(secretaryId).get()
                        .addOnSuccessListener { secretarySnapshot ->
                            if (secretarySnapshot.exists()) {
                                val secretaryName = secretarySnapshot.child("name").value?.toString() ?: "Unknown"
                                Log.d("ChatActivity", "Secretary Found: $secretaryName")
                                tvSecretaryName.text = "Chat with $secretaryName"
                            } else {
                                Log.e("ChatActivity", "Secretary not found! Searched ID: $secretaryId")
                                tvSecretaryName.text = "Secretary not found"
                            }
                        }.addOnFailureListener { exception ->
                            Log.e("ChatActivity", "Error fetching secretary: ${exception.message}")
                        }
                } else {
                    Log.e("ChatActivity", "Secretary ID is missing or empty for lawyer: $lawyerId")
                    tvSecretaryName.text = "No secretary assigned"
                }
            } else {
                Log.e("ChatActivity", "Lawyer not found in database")
                tvSecretaryName.text = "Lawyer not found"
            }
        }.addOnFailureListener { exception ->
            Log.e("ChatActivity", "Error fetching lawyer: ${exception.message}")
        }
    }



}



