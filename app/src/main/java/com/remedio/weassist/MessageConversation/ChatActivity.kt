package com.remedio.weassist.MessageConversation

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Clients.MessageAdapter
import com.remedio.weassist.R

class ChatActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvChatPartnerName: TextView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var messagesAdapter: MessageAdapter
    private lateinit var backButton: ImageButton
    private val messagesList = mutableListOf<Message>()

    // Chat participant IDs
    private var clientId: String? = null
    private var secretaryId: String? = null
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var conversationId: String? = null

    // Current user type
    private var userType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        tvChatPartnerName = findViewById(R.id.name_secretary)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        backButton = findViewById(R.id.back_button)

        database = FirebaseDatabase.getInstance().reference
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Get conversation ID from intent if available
        conversationId = intent.getStringExtra("CONVERSATION_ID")

        // Get user IDs from intent
        secretaryId = intent.getStringExtra("SECRETARY_ID")
        clientId = intent.getStringExtra("CLIENT_ID")

        Log.d("ChatActivity", "Received in intent - conversationId: $conversationId, secretaryId: $secretaryId, clientId: $clientId")

        // Set up RecyclerView
        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

        // Determine current user type and set up chat
        if (currentUserId != null) {
            determineCurrentUserType()
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun determineCurrentUserType() {
        if (currentUserId == null) {
            Log.e("ChatActivity", "No current user ID found!")
            finish()
            return
        }

        // Check if current user is a secretary
        database.child("secretaries").child(currentUserId!!).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    userType = "secretary"
                    Log.d("ChatActivity", "Current user is a secretary")

                    // If we have a conversation ID but no client ID, try to extract it
                    if (clientId == null && conversationId != null) {
                        extractClientIdFromConversation(conversationId!!)
                    } else {
                        setupChatPartner()
                    }
                    return@addOnSuccessListener
                }

                // If not secretary, assume client
                userType = "client"
                Log.d("ChatActivity", "Current user is a client")

                // If we have a conversation ID but no secretary ID, try to extract it
                if (secretaryId == null && conversationId != null) {
                    extractSecretaryIdFromConversation(conversationId!!)
                } else {
                    setupChatPartner()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error determining user type: ${e.message}")
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun extractClientIdFromConversation(conversationId: String) {
        database.child("conversations").child(conversationId).child("participantIds")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (participantSnapshot in snapshot.children) {
                            val participantId = participantSnapshot.key
                            if (participantId != currentUserId) {
                                // Verify this is a client
                                database.child("Users").child(participantId!!).get()
                                    .addOnSuccessListener { userSnapshot ->
                                        if (userSnapshot.exists()) {
                                            clientId = participantId
                                            Log.d("ChatActivity", "Extracted clientId: $clientId from conversation")
                                            setupChatPartner()
                                        }
                                    }
                            }
                        }
                    } else {
                        Log.e("ChatActivity", "No participants found in conversation")
                        Toast.makeText(applicationContext, "Conversation data not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching conversation participants: ${error.message}")
                }
            })
    }

    private fun extractSecretaryIdFromConversation(conversationId: String) {
        database.child("conversations").child(conversationId).child("participantIds")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (participantSnapshot in snapshot.children) {
                            val participantId = participantSnapshot.key
                            if (participantId != currentUserId) {
                                // Verify this is a secretary
                                database.child("secretaries").child(participantId!!).get()
                                    .addOnSuccessListener { secretarySnapshot ->
                                        if (secretarySnapshot.exists()) {
                                            secretaryId = participantId
                                            Log.d("ChatActivity", "Extracted secretaryId: $secretaryId from conversation")
                                            setupChatPartner()
                                        }
                                    }
                            }
                        }
                    } else {
                        Log.e("ChatActivity", "No participants found in conversation")
                        Toast.makeText(applicationContext, "Conversation data not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching conversation participants: ${error.message}")
                }
            })
    }

    private fun setupChatPartner() {
        if (userType == null) {
            // User type not determined yet, wait for determineCurrentUserType to complete
            return
        }

        when (userType) {
            "client" -> {
                // Client chatting with secretary
                if (secretaryId != null) {
                    getSecretaryName(secretaryId!!)
                } else {
                    Log.e("ChatActivity", "No secretary ID provided!")
                    Toast.makeText(this, "Secretary information not available", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            "secretary" -> {
                // Secretary chatting with client
                if (clientId != null) {
                    getClientName(clientId!!)
                } else {
                    Log.e("ChatActivity", "No client ID provided!")
                    Toast.makeText(this, "Client information not available", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun getClientName(clientId: String) {
        database.child("Users").child(clientId).get()
            .addOnSuccessListener { clientSnapshot ->
                if (clientSnapshot.exists()) {
                    val firstName = clientSnapshot.child("firstName").value?.toString() ?: "Unknown"
                    val lastName = clientSnapshot.child("lastName").value?.toString() ?: ""
                    val fullName = "$firstName $lastName".trim()
                    tvChatPartnerName.text = fullName
                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Client not found!")
                    Toast.makeText(this, "Client information not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error fetching client data: ${e.message}")
                Toast.makeText(this, "Failed to load client information", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun getSecretaryName(secretaryId: String) {
        database.child("secretaries").child(secretaryId).get()
            .addOnSuccessListener { secretarySnapshot ->
                if (secretarySnapshot.exists()) {
                    val secretaryName = secretarySnapshot.child("name").value?.toString() ?: "Unknown"
                    tvChatPartnerName.text = secretaryName
                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Secretary not found!")
                    Toast.makeText(this, "Secretary information not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error fetching secretary data: ${e.message}")
                Toast.makeText(this, "Failed to load secretary information", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun sendMessage() {
        val messageText = etMessageInput.text.toString().trim()
        Log.d("ChatActivity", "Button Clicked! Message: $messageText")

        if (messageText.isNotEmpty() && currentUserId != null) {
            val receiverId = determineReceiverId()
            if (receiverId == null) {
                Log.e("ChatActivity", "Could not determine receiver ID")
                Toast.makeText(this, "Unable to send message: recipient not identified", Toast.LENGTH_SHORT).show()
                return
            }

            val conversationId = generateConversationId(currentUserId!!, receiverId)

            val message = Message(
                senderId = currentUserId!!,
                receiverId = receiverId,
                message = messageText,
                timestamp = System.currentTimeMillis()
            )

            val chatRef = database.child("conversations").child(conversationId).child("messages").push()

            // First, add the message to the conversation
            chatRef.setValue(message).addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("ChatActivity", "Message Sent Successfully!")

                    // Create notification for the recipient
                    createNotificationForRecipient(message)

                    // Now, add the participant IDs to the conversation
                    val participantsMap = mapOf(
                        currentUserId!! to true,
                        receiverId to true
                    )

                    // Add participants to the conversation if they are not already there
                    database.child("conversations").child(conversationId).child("participantIds")
                        .updateChildren(participantsMap).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                Log.d("ChatActivity", "Participants added successfully!")
                            } else {
                                Log.e("ChatActivity", "Failed to add participants: ${updateTask.exception?.message}")
                            }
                        }

                    etMessageInput.text.clear()  // Clear the input field
                } else {
                    Log.e("ChatActivity", "Failed to send message: ${it.exception?.message}")
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.e("ChatActivity", "Message cannot be sent! Check messageText or currentUserId.")
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun determineReceiverId(): String? {
        return when (userType) {
            "client" -> secretaryId
            "secretary" -> clientId
            else -> null
        }
    }

    private fun createNotificationForRecipient(message: Message) {
        val recipientId = message.receiverId

        // Create notification data
        val notification = hashMapOf(
            "senderId" to message.senderId,
            "message" to message.message,
            "timestamp" to message.timestamp,
            "type" to "message",
            "isRead" to false,
            "conversationId" to generateConversationId(message.senderId, message.receiverId)
        )

        // Add notification to recipient's notifications list
        database.child("notifications").child(recipientId).push()
            .setValue(notification)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Notification created for recipient: $recipientId")

                // Increment unread message counter for recipient
                val conversationId = generateConversationId(message.senderId, message.receiverId)
                incrementUnreadCounter(conversationId, recipientId)
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to create notification: ${e.message}")
            }
    }

    private fun incrementUnreadCounter(conversationId: String, userId: String) {
        val unreadRef = database.child("conversations").child(conversationId)
            .child("unreadMessages").child(userId)

        unreadRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                unreadRef.setValue(currentCount + 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error updating unread count: ${error.message}")
            }
        })
    }

    private fun generateConversationId(user1: String, user2: String): String {
        return if (user1 < user2) {
            "conversation_${user1}_${user2}"
        } else {
            "conversation_${user2}_${user1}"
        }
    }

    private fun listenForMessages() {
        if (currentUserId == null) {
            Log.e("ChatActivity", "Current user ID is null!")
            return
        }

        val receiverId = determineReceiverId()
        if (receiverId == null) {
            Log.e("ChatActivity", "Could not determine receiver ID for messages")
            return
        }

        // If we have a conversation ID from intent, use it directly
        val actualConversationId = conversationId ?: generateConversationId(currentUserId!!, receiverId)
        Log.d("ChatActivity", "Listening for messages with conversationId: $actualConversationId")

        // Reset unread messages counter for this user
        database.child("conversations").child(actualConversationId)
            .child("unreadMessages")
            .child(currentUserId!!)
            .setValue(0)

        // Listen for messages
        val messagesRef = database.child("conversations").child(actualConversationId).child("messages")
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()
                if (!snapshot.exists()) {
                    Log.d("ChatActivity", "No messages found in conversation: $actualConversationId")
                    return
                }

                Log.d("ChatActivity", "Found ${snapshot.childrenCount} messages")
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        messagesList.add(message)
                    }
                }
                messagesAdapter.notifyDataSetChanged()
                if (messagesList.isNotEmpty()) {
                    rvChatMessages.scrollToPosition(messagesList.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error fetching messages: ${error.message}")
                Toast.makeText(applicationContext, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        })
    }
}