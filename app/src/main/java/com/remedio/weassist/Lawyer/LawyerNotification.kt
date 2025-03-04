package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

import com.remedio.weassist.R

class LawyerNotification : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_notification)

        notificationTextView = findViewById(R.id.recentNotification) // Assuming this ID exists in XML
        auth = FirebaseAuth.getInstance()
        val lawyerId = auth.currentUser?.uid

        if (lawyerId != null) {
            fetchNotifications(lawyerId)
        }
    }

    private fun fetchNotifications(lawyerId: String) {
        database = FirebaseDatabase.getInstance().getReference("notifications").child(lawyerId)

        database.orderByChild("timestamp").limitToLast(5).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val notifications = snapshot.children.mapNotNull {
                        it.child("message").getValue(String::class.java)
                    }.joinToString("\n\n")

                    notificationTextView.text = notifications
                } else {
                    notificationTextView.text = "No new notifications."
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LawyerNotification", "Failed to fetch notifications: ${error.message}")
            }
        })
    }
}
