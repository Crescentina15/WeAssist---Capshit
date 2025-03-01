package com.remedio.weassist.Secretary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Clients.MessagesAdapter
import com.remedio.weassist.R

class SecretaryMessageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var databaseReference: DatabaseReference
    private lateinit var messagesAdapter: MessagesAdapter
    private val messagesList = mutableListOf<Message>()
    private var secretaryId: String? = null
    private var clientId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            clientId = it.getString("CLIENT_ID")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_secretary_message, container, false)

        recyclerView = view.findViewById(R.id.inbox_recycler_view)
        messageInput = view.findViewById(R.id.editTextMessage)
        sendButton = view.findViewById(R.id.buttonSend)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesAdapter = MessagesAdapter(messagesList)
        recyclerView.adapter = messagesAdapter

        secretaryId = FirebaseAuth.getInstance().currentUser?.uid

        if (secretaryId.isNullOrEmpty() || clientId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "User authentication failed", Toast.LENGTH_SHORT).show()
            return view
        }

        // Ensure messages are stored under both nodes
        databaseReference = FirebaseDatabase.getInstance().getReference("messages")
        loadMessages()

        sendButton.setOnClickListener {
            sendMessage()
        }

        return view
    }

    private fun loadMessages() {
        val messageRef = databaseReference.child(secretaryId!!).child(clientId!!)
        messageRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                message?.let {
                    messagesList.add(it)
                    messagesAdapter.notifyItemInserted(messagesList.size - 1)
                    recyclerView.scrollToPosition(messagesList.size - 1)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(requireContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val message = Message(secretaryId!!, clientId!!, messageText, System.currentTimeMillis())

        val clientMessageRef = databaseReference.child(clientId!!).child(secretaryId!!).push()
        val secretaryMessageRef = databaseReference.child(secretaryId!!).child(clientId!!).push()

        val messageData = mapOf(
            clientMessageRef.key!! to message,
            secretaryMessageRef.key!! to message
        )

        databaseReference.updateChildren(messageData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                messageInput.text.clear()
            } else {
                Toast.makeText(requireContext(), "Failed to send message", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
