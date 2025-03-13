package com.remedio.weassist.Secretary


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class SecretaryAppointmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_appointment)

        // Get the appointment ID from the intent
        val appointmentId = intent.getStringExtra("APPOINTMENT_ID")

        // Create fragment instance and pass the appointment ID
        val fragment = SecretaryAppointmentFragment().apply {
            arguments = Bundle().apply {
                putString("APPOINTMENT_ID", appointmentId)
            }
        }

        // Add the fragment to the activity
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}