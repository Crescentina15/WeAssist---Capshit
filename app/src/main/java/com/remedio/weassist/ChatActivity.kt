package com.remedio.weassist

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Clients.MessageAdapter

class ChatActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvSecretaryName: TextView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var messagesAdapter: MessageAdapter
    private lateinit var backButton: ImageButton
    private val messagesList = mutableListOf<Message>()

    private var lawyerId: String? = null
    private var secretaryId: String? = null
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        tvSecretaryName = findViewById(R.id.name_secretary)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        backButton = findViewById(R.id.back_button) // Back button initialization

        database = FirebaseDatabase.getInstance().reference
        lawyerId = intent.getStringExtra("LAWYER_ID")

        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

        if (lawyerId != null) {
            getSecretaryName(lawyerId!!)
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
        }

        // Handle back button click
        backButton.setOnClickListener {
            finish() // Closes the activity and goes back to the previous screen
        }
    }

    private fun getSecretaryName(lawyerId: String) {
        Log.d("ChatActivity", "Fetching secretary for lawyerId: $lawyerId") // Debug log

        database.child("lawyers").child(lawyerId).get().addOnSuccessListener { lawyerSnapshot ->
            if (lawyerSnapshot.exists()) {
                secretaryId = lawyerSnapshot.child("secretaryID").value?.toString()

                Log.d("ChatActivity", "Retrieved secretaryId: $secretaryId") // Debug log

                if (!secretaryId.isNullOrEmpty()) {
                    database.child("secretaries").child(secretaryId!!).get()
                        .addOnSuccessListener { secretarySnapshot ->
                            if (secretarySnapshot.exists()) {
                                val secretaryName = secretarySnapshot.child("name").value?.toString() ?: "Unknown"
                                tvSecretaryName.text = "$secretaryName"

                                Log.d("ChatActivity", "Secretary Name: $secretaryName") // Debug log

                                listenForMessages()
                            } else {
                                Log.e("ChatActivity", "Secretary not found!")
                            }
                        }
                } else {
                    Log.e("ChatActivity", "No secretary ID found for lawyer!")
                }
            } else {
                Log.e("ChatActivity", "Lawyer not found!")
            }
        }
    }


    private fun sendMessage() {
        val messageText = etMessageInput.text.toString().trim()
        Log.d("ChatActivity", "Button Clicked! Message: $messageText") // Debug log

        if (messageText.isNotEmpty() && !secretaryId.isNullOrEmpty() && currentUserId != null) {
            val conversationId = generateConversationId(currentUserId!!, secretaryId!!) // Generate conversation ID

            val message = Message(
                senderId = currentUserId!!,
                receiverId = secretaryId!!,
                message = messageText
            )

            val chatRef = database.child("conversations").child(conversationId).child("messages").push()
            chatRef.setValue(message).addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("ChatActivity", "Message Sent Successfully!")
                    etMessageInput.text.clear()
                } else {
                    Log.e("ChatActivity", "Failed to send message: ${it.exception?.message}")
                }
            }
        } else {
            Log.e("ChatActivity", "Message cannot be sent! Check messageText, secretaryId, or currentUserId.")
        }
    }


    /**
     * Generates a unique conversation ID for two users
     */
    private fun generateConversationId(user1: String, user2: String): String {
        return if (user1 < user2) {
            "conversation_${user1}_${user2}"
        } else {
            "conversation_${user2}_${user1}"
        }
    }


    private fun listenForMessages() {
        if (secretaryId.isNullOrEmpty() || currentUserId == null) return

        val conversationId = generateConversationId(currentUserId!!, secretaryId!!) // Get conversation ID
        val messagesRef = database.child("conversations").child(conversationId).child("messages")

        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        messagesList.add(message)
                    }
                }
                messagesAdapter.notifyDataSetChanged()
                rvChatMessages.scrollToPosition(messagesList.size - 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error fetching messages: ${error.message}")
            }
        })
    }

}
