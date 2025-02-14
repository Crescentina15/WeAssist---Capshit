package com.remedio.weassist.Secretary

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class AddAvailabilityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_availability)

        val lawyerName = intent.getStringExtra("LAWYER_NAME")
        val lawyerId = intent.getStringExtra("LAWYER_ID")
        val lawFirm = intent.getStringExtra("LAW_FIRM")

        val lawyerNameTextView: TextView = findViewById(R.id.lawyer_name)
        lawyerNameTextView.text = "Adding Availability for $lawyerName"
    }
}
