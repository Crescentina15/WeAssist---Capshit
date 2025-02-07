package com.remedio.weassist.Miscellaneous

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class ReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        // Back button functionality
        findViewById<ImageButton>(R.id.back_arrow)?.setOnClickListener {
            finish()
        }
    }
}