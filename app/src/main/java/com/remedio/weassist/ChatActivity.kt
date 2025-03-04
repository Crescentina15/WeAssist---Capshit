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
    }

    private fun getSecretaryName(lawyerId: String) {
        database.child("lawyers").child(lawyerId).get().addOnSuccessListener { lawyerSnapshot ->
            if (lawyerSnapshot.exists()) {
                secretaryId = lawyerSnapshot.child("secretaryID").value?.toString()

                if (!secretaryId.isNullOrEmpty()) {
                    database.child("secretaries").child(secretaryId!!).get()
                        .addOnSuccessListener { secretarySnapshot ->
                            if (secretarySnapshot.exists()) {
                                val secretaryName = secretarySnapshot.child("name").value?.toString() ?: "Unknown"
                                tvSecretaryName.text = "Chat with Secretary $secretaryName"

                                listenForMessages()
                            }
                        }
                }
            }
        }
    }

    private fun sendMessage() {
        val messageText = etMessageInput.text.toString().trim()

        if (messageText.isNotEmpty() && !secretaryId.isNullOrEmpty() && currentUserId != null) {
            val message = Message(
                senderId = currentUserId!!,
                receiverId = secretaryId!!,
                message = messageText
            )

            val chatRef = database.child("messages").push()
            chatRef.setValue(message).addOnCompleteListener {
                if (it.isSuccessful) {
                    etMessageInput.text.clear()
                } else {
                    Log.e("ChatActivity", "Failed to send message: ${it.exception?.message}")
                }
            }
        }
    }

    private fun listenForMessages() {
        val messagesRef = database.child("messages")
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)
                    if (message != null && (message.senderId == currentUserId || message.receiverId == currentUserId)) {
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
