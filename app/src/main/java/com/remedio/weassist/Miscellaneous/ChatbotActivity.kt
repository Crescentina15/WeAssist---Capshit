package com.remedio.weassist.Miscellaneous

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ai.client.generativeai.GenerativeModel
import com.remedio.weassist.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatbotActivity : AppCompatActivity() {

    private lateinit var chatTextView: TextView
    private lateinit var userInputEditText: EditText
    private lateinit var sendButton: Button

    private val apiKey = "AIzaSyAdbLaB4lv39wPSnNAj5G8Ono0_BpGSPlM"  // 🔹 Replace with your Google Gemini API key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        chatTextView = findViewById(R.id.chatTextView)
        userInputEditText = findViewById(R.id.userInputEditText)
        sendButton = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            val userMessage = userInputEditText.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                chatTextView.append("\nYou: $userMessage")
                userInputEditText.text.clear()
                sendMessageToGemini(userMessage)  // 🔹 Send message to Gemini AI
            }
        }
    }

    private fun sendMessageToGemini(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val generativeModel = GenerativeModel(modelName = "gemini-pro", apiKey = apiKey)
                val response = generativeModel.generateContent(message) // 🔹 FIXED HERE

                val botReply = response.text ?: "Sorry, I didn't understand that."

                withContext(Dispatchers.Main) {
                    chatTextView.append("\nBot: $botReply")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    chatTextView.append("\nBot: Error getting response.")
                }
            }
        }
    }



}
