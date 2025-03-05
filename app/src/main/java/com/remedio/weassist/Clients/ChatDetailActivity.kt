package com.remedio.weassist.Clients

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
import com.remedio.weassist.R

class ChatDetailActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvChatPartnerName: TextView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var messagesAdapter: MessageAdapter
    private lateinit var backButton: ImageButton
    private val messagesList = mutableListOf<Message>()

    private var chatPartnerId: String? = null
    private var conversationId: String? = null
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        // Initialize views
        tvChatPartnerName = findViewById(R.id.tvChatPartnerName)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        backButton = findViewById(R.id.backButton)

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance().reference

        // Retrieve chat partner details from intent
        chatPartnerId = intent.getStringExtra("CHAT_PARTNER_ID")
        val chatPartnerName = intent.getStringExtra("CHAT_PARTNER_NAME")

        // Set chat partner name
        tvChatPartnerName.text = chatPartnerName ?: "Chat Partner"

        // Generate conversation ID
        conversationId = generateConversationId()

        // Setup RecyclerView
        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

        // Send message button click listener
        btnSendMessage.setOnClickListener {
            sendMessage()
        }

        // Back button click listener
        backButton.setOnClickListener {
            finish()
        }

        // Start listening for messages if conversation ID is available
        if (conversationId != null) {
            listenForMessages()
        }
    }

    private fun generateConversationId(): String? {
        return if (currentUserId != null && chatPartnerId != null) {
            if (currentUserId!! < chatPartnerId!!) {
                "conversation_${currentUserId}_${chatPartnerId}"
            } else {
                "conversation_${chatPartnerId}_${currentUserId}"
            }
        } else {
            null
        }
    }

    private fun sendMessage() {
        val messageText = etMessageInput.text.toString().trim()

        if (messageText.isNotEmpty() && chatPartnerId != null && currentUserId != null && conversationId != null) {
            val message = Message(
                senderId = currentUserId!!,
                receiverId = chatPartnerId!!,
                message = messageText,
                timestamp = System.currentTimeMillis()
            )

            // Reference to specific conversation
            val conversationRef = database.child("conversations").child(conversationId!!).child("messages")

            // Create a new message in the messages node
            val newMessageRef = conversationRef.push()
            newMessageRef.setValue(message).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("ChatDetailActivity", "Message sent successfully")
                    etMessageInput.text.clear()
                } else {
                    Log.e("ChatDetailActivity", "Failed to send message", task.exception)
                }
            }
        }
    }

    private fun listenForMessages() {
        if (conversationId == null) return

        val conversationMessagesRef = database.child("conversations").child(conversationId!!).child("messages")

        conversationMessagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()

                for (childSnapshot in snapshot.children) {
                    val message = childSnapshot.getValue(Message::class.java)

                    if (message != null) {
                        messagesList.add(message)
                    }
                }

                // Sort messages by timestamp
                messagesList.sortBy { it.timestamp }

                messagesAdapter.notifyDataSetChanged()

                // Scroll to the last message
                if (messagesList.isNotEmpty()) {
                    rvChatMessages.scrollToPosition(messagesList.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatDetailActivity", "Error fetching messages", error.toException())
            }
        })
    }
}