package com.remedio.weassist.MessageConversation

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
import com.remedio.weassist.R

class ChatActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvSecretaryName: TextView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var messagesAdapter: MessageAdapter
    private lateinit var backButton: ImageButton
    private val messagesList = mutableListOf<Message>()
    private var clientId: String? = null

    private var lawyerId: String? = null
    private var secretaryId: String? = null
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    // Update the onCreate method to retrieve clientId from intent
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        tvSecretaryName = findViewById(R.id.name_secretary)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        backButton = findViewById(R.id.back_button)

        database = FirebaseDatabase.getInstance().reference
        lawyerId = intent.getStringExtra("LAWYER_ID")
        secretaryId = intent.getStringExtra("SECRETARY_ID")
        clientId = intent.getStringExtra("CLIENT_ID")
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

        // If SECRETARY_ID is provided with CLIENT_ID, this is secretary-client chat
        if (secretaryId != null && clientId != null) {
            getClientName(clientId!!)
        }
        // If SECRETARY_ID is provided alone, use it directly (client viewing secretary)
        else if (secretaryId != null) {
            getSecretaryNameDirect(secretaryId!!)
        }
        // Otherwise, fetch the secretary based on lawyer ID
        else if (lawyerId != null) {
            getSecretaryName(lawyerId!!)
        } else {
            Log.e("ChatActivity", "No valid IDs provided!")
            finish()
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    // Add a method to get client name
    private fun getClientName(clientId: String) {
        database.child("Users").child(clientId).get()
            .addOnSuccessListener { clientSnapshot ->
                if (clientSnapshot.exists()) {
                    val firstName = clientSnapshot.child("firstName").value?.toString() ?: "Unknown"
                    val lastName = clientSnapshot.child("lastName").value?.toString() ?: ""
                    val fullName = "$firstName $lastName".trim()
                    tvSecretaryName.text = fullName
                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Client not found!")
                }
            }
    }


    private fun getSecretaryNameDirect(secretaryId: String) {
        database.child("secretaries").child(secretaryId).get()
            .addOnSuccessListener { secretarySnapshot ->
                if (secretarySnapshot.exists()) {
                    val secretaryName = secretarySnapshot.child("name").value?.toString() ?: "Unknown"
                    tvSecretaryName.text = secretaryName
                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Secretary not found!")
                }
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
        Log.d("ChatActivity", "Button Clicked! Message: $messageText")

        if (messageText.isNotEmpty() && currentUserId != null) {
            val receiverId = when {
                clientId != null -> clientId!!
                secretaryId != null -> secretaryId!!
                else -> {
                    Log.e("ChatActivity", "No receiver ID available")
                    return
                }
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
                }
            }
        } else {
            Log.e("ChatActivity", "Message cannot be sent! Check messageText or currentUserId.")
        }
    }

    /**
     * Creates a notification entry in Firebase for the message recipient
     */
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

    /**
     * Increments the unread messages counter for a user in a specific conversation
     */
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


    // Update listenForMessages to handle client IDs
    private fun listenForMessages() {
        if (currentUserId == null) {
            Log.e("ChatActivity", "Current user ID is null!")
            return
        }

        // Log the IDs to help with debugging
        Log.d("ChatActivity", "Listening for messages with currentUserId: $currentUserId")
        Log.d("ChatActivity", "clientId: $clientId, secretaryId: $secretaryId")

        val receiverId = when {
            clientId != null -> {
                Log.d("ChatActivity", "Using clientId as receiverId: $clientId")
                clientId!!
            }
            secretaryId != null -> {
                Log.d("ChatActivity", "Using secretaryId as receiverId: $secretaryId")
                secretaryId!!
            }
            else -> {
                Log.e("ChatActivity", "No receiver ID available")
                return
            }
        }

        // Generate conversation ID consistently
        val conversationId = generateConversationId(currentUserId!!, receiverId)
        Log.d("ChatActivity", "Generated conversationId: $conversationId")

        // Reset unread messages counter for this user
        database.child("conversations").child(conversationId)
            .child("unreadMessages")
            .child(currentUserId!!)
            .setValue(0)

        // Listen for messages
        val messagesRef = database.child("conversations").child(conversationId).child("messages")
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()
                if (!snapshot.exists()) {
                    Log.d("ChatActivity", "No messages found in conversation: $conversationId")
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
            }
        })
    }

}
