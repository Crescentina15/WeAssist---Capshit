package com.remedio.weassist.MessageConversation

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.remedio.weassist.R

class ImagePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val imageView: PhotoView = findViewById(R.id.fullscreen_image)
        val closeButton: ImageButton = findViewById(R.id.close_button)

        val imageUrl = intent.getStringExtra("image_url")
        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .into(imageView)
        }

        closeButton.setOnClickListener {
            finish()
        }

        // Hide system UI for fullscreen experience
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        supportActionBar?.hide()
    }
}