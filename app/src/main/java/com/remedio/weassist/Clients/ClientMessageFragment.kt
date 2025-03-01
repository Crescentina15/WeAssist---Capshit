package com.remedio.weassist.Clients

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
import com.remedio.weassist.R

class ClientMessageFragment : Fragment() {

    private var lawyerId: String? = null
    private var secretaryId: String? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var databaseReference: DatabaseReference
    private lateinit var messagesAdapter: MessagesAdapter
    private val messagesList = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            lawyerId = it.getString("LAWYER_ID")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_message, container, false)

        recyclerView = view.findViewById(R.id.inbox_recycler_view)
        messageInput = view.findViewById(R.id.editTextMessage)
        sendButton = view.findViewById(R.id.buttonSend)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesAdapter = MessagesAdapter(messagesList)
        recyclerView.adapter = messagesAdapter

        val clientId = FirebaseAuth.getInstance().currentUser?.uid
        if (clientId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return view
        }

        // Retrieve the secretary assigned to the lawyer based on lawFirm
        getSecretaryIdForLawyer(lawyerId, clientId)

        sendButton.setOnClickListener {
            if (!secretaryId.isNullOrEmpty()) {
                sendMessage(clientId, secretaryId!!)
            } else {
                Toast.makeText(requireContext(), "No secretary assigned", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun getSecretaryIdForLawyer(lawyerId: String?, clientId: String) {
        if (lawyerId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No lawyer assigned", Toast.LENGTH_SHORT).show()
            return
        }

        // Step 1: Get the lawyer's lawFirm
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)
        lawyerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                if (lawyerSnapshot.exists()) {
                    val lawyerLawFirm = lawyerSnapshot.child("lawFirm").getValue(String::class.java)
                    if (lawyerLawFirm.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), "Lawyer's law firm not found", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Step 2: Query the secretaries node to find a secretary with matching lawFirm
                    val secretariesRef = FirebaseDatabase.getInstance().getReference("secretaries")
                    secretariesRef.orderByChild("lawFirm").equalTo(lawyerLawFirm)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(secretariesSnapshot: DataSnapshot) {
                                if (secretariesSnapshot.exists()) {
                                    for (secretarySnapshot in secretariesSnapshot.children) {
                                        secretaryId = secretarySnapshot.key // Get the secretary's ID
                                        if (!secretaryId.isNullOrEmpty()) {
                                            setupDatabase(clientId, secretaryId!!)
                                            return
                                        }
                                    }
                                }
                                // If no secretary is found
                                Toast.makeText(requireContext(), "No secretary assigned to this law firm", Toast.LENGTH_SHORT).show()
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Toast.makeText(requireContext(), "Failed to get secretary information", Toast.LENGTH_SHORT).show()
                            }
                        })
                } else {
                    Toast.makeText(requireContext(), "Lawyer data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load lawyer data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupDatabase(clientId: String, secretaryId: String) {
        databaseReference = FirebaseDatabase.getInstance().getReference("messages")
        loadMessages(clientId, secretaryId)
    }

    private fun loadMessages(clientId: String, secretaryId: String) {
        val messageRef = databaseReference.child(clientId).child(secretaryId)
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

    private fun sendMessage(clientId: String, secretaryId: String) {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(requireContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val message = Message(clientId, secretaryId, messageText, System.currentTimeMillis())

        val clientMessageRef = databaseReference.child(clientId).child(secretaryId).push()
        val secretaryMessageRef = databaseReference.child(secretaryId).child(clientId).push()

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