package com.remedio.weassist.Clients

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.remedio.weassist.R

class ClientMessageFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private lateinit var messagesRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_message, container, false)

        recyclerView = view.findViewById(R.id.inbox_recycler_view)
        editTextMessage = view.findViewById(R.id.editTextMessage)
        buttonSend = view.findViewById(R.id.buttonSend)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        currentUser = FirebaseAuth.getInstance().currentUser!!
        messagesRef = FirebaseDatabase.getInstance().getReference("messages")

        messageAdapter = MessageAdapter(messageList, currentUser.uid)
        recyclerView.adapter = messageAdapter

        loadMessages()
        buttonSend.setOnClickListener { sendMessage() }

        return view
    }

    private fun loadMessages() {
        val currentUserId = currentUser.uid
        messagesRef.orderByChild("timestamp").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                if (message != null && (message.receiverId == currentUserId || message.senderId == currentUserId)) {
                    messageList.add(message)
                    messageAdapter.notifyItemInserted(messageList.size - 1)
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onChildRemoved(snapshot: DataSnapshot) {}

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Failed to load messages", error.toException())
            }
        })
    }


    private fun sendMessage() {
        val senderId = currentUser.uid
        val messageText = editTextMessage.text.toString().trim()

        if (messageText.isNotEmpty()) {
            val receiverId = "6rTJ9u2n3SdjhjSJiq3EAxGl5653" // Replace with actual receiver logic
            val timestamp = System.currentTimeMillis()

            val message = Message(
                senderId = senderId,
                receiverId = receiverId,
                message = messageText,  // ✅ Use "message" to match the model
                timestamp = timestamp
            )

            messagesRef.push().setValue(message).addOnSuccessListener {
                editTextMessage.text.clear()
            }.addOnFailureListener {
                Log.e("FirebaseError", "Failed to send message", it)
            }
        }
    }
}
