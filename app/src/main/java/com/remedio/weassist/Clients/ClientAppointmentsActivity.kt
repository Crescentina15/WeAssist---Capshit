package com.remedio.weassist.Clients


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class ClientAppointmentsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_appointments)

        // Only add the fragment if this is the first creation
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ClientAppointmentsFragment())
                .commit()
        }
    }
}