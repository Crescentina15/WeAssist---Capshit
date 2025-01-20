package com.remedio.weassist

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R
import com.remedio.weassist.registerSelection

class MainActivity : AppCompatActivity() {

    private val SPLASH_DISPLAY_LENGTH = 3000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Using Handler to delay the transition
        Handler().postDelayed({
            // Start the next activity
            val mainIntent = Intent(this, Login::class.java)
            startActivity(mainIntent)
            finish() // Close the splash activity so the user won't go back to it
        }, SPLASH_DISPLAY_LENGTH.toLong())
    }
}