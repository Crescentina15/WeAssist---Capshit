package com.remedio.weassist.Clients

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class ClientNotificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification) // Ensure this XML file exists

        // Using findViewById instead of ViewBinding
        val recentTitle = findViewById<TextView>(R.id.recentTitle)
        val recentNotification = findViewById<TextView>(R.id.recentNotification)
        val recentTime = findViewById<TextView>(R.id.recentTime)

        // Example of setting text dynamically
        recentTitle.text = "Recent Notifications"
        recentNotification.text = "Your appointment has been approved."
        recentTime.text = "October 24, 2024 12:45 PM"
    }
}
