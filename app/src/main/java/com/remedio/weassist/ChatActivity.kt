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


    // Update sendMessage method to handle client IDs
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
        if (currentUserId == null) return

        val receiverId = when {
            clientId != null -> clientId!!
            secretaryId != null -> secretaryId!!
            else -> {
                Log.e("ChatActivity", "No receiver ID available")
                return
            }
        }

        val conversationId = generateConversationId(currentUserId!!, receiverId)
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
