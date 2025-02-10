package com.remedio.weassist.Secretary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class SecretaryDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_dashboard)

        // Load the SecretaryDashboardFragment into the Activity
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container1, SecretaryDashboardFragment())
        fragmentTransaction.commit()
    }
}
