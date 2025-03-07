package com.remedio.weassist.Miscellaneous

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.remedio.weassist.Lawyer.Lawyer
import com.remedio.weassist.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatbotActivity : AppCompatActivity() {

    private lateinit var chatTextView: TextView
    private lateinit var userInputEditText: EditText
    private lateinit var sendButton: Button

    private val apiKey = "AIzaSyBowtsZuE65SZU-S_kxShCLjcYLa1OueTw"  // 🔹 Replace with your Google Gemini API key

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

                // 🔹 Handle query intelligently
                handleUserQuery(userMessage)
            }
        }
    }

    /**
     * Determines what type of query the user is asking and routes it accordingly.
     */
    private fun handleUserQuery(userQuery: String) {
        when {
            userQuery.contains("recommend", ignoreCase = true) && userQuery.contains("lawyer", ignoreCase = true) -> {
                fetchLawyersFromFirebase(userQuery)
            }
            userQuery.contains("how to", ignoreCase = true) || userQuery.contains("legal advice", ignoreCase = true) -> {
                askGeminiForLegalAdvice(userQuery)
            }
            userQuery.contains("schedule", ignoreCase = true) && userQuery.contains("appointment", ignoreCase = true) -> {
                scheduleAppointment(userQuery)
            }
            else -> {
                askGeminiForGeneralQuery(userQuery)
            }
        }
    }

    /**
     * Fetches recommended lawyers from Firebase Cloud Functions.
     */
    private fun fetchLawyersFromFirebase(query: String) {
        val database = FirebaseDatabase.getInstance().getReference("lawyers")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawyersList = StringBuilder("\nAvailable Lawyers:\n")

                    for (lawyerSnapshot in snapshot.children) {
                        val lawyer = lawyerSnapshot.getValue(Lawyer::class.java)
                        lawyer?.let {
                            lawyersList.append("• ${it.name} - ${it.lawFirm}\n")
                        }
                    }

                    chatTextView.append("\nBot: $lawyersList")
                } else {
                    chatTextView.append("\nBot: No lawyers found.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error fetching lawyers", error.toException())
                chatTextView.append("\nBot: Error fetching lawyer data.")
            }
        })
    }


    /**
     * Sends legal-related queries to Gemini AI.
     */
    private fun askGeminiForLegalAdvice(query: String) {
        sendMessageToGemini("Provide legal advice: $query")
    }

    /**
     * Sends general queries to Gemini AI.
     */
    private fun askGeminiForGeneralQuery(query: String) {
        sendMessageToGemini("General legal chatbot response: $query")
    }

    /**
     * Calls Firebase Cloud Function to schedule an appointment.
     */
    private fun scheduleAppointment(query: String) {
        val data = mapOf("query" to query)

        FirebaseFunctions.getInstance()
            .getHttpsCallable("scheduleAppointment")
            .call(data)
            .addOnSuccessListener { result ->
                val response = (result.data as Map<*, *>)["response"] as String
                chatTextView.append("\nBot: $response")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error scheduling appointment", e)
                chatTextView.append("\nBot: Error scheduling appointment.")
            }
    }

    /**
     * Sends a message to Google Gemini AI and displays the response.
     */
    private fun sendMessageToGemini(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val generativeModel = GenerativeModel(modelName = "gemini-1.5-pro-latest", apiKey = apiKey)

                val response = generativeModel.generateContent(message)
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
