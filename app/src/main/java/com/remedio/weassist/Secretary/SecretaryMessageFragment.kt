package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Clients.MessageAdapter
import com.remedio.weassist.R

class SecretaryMessageFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private lateinit var messagesRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_secretary_message, container, false)

        recyclerView = view.findViewById(R.id.inbox_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        currentUser = FirebaseAuth.getInstance().currentUser!!
        messagesRef = FirebaseDatabase.getInstance().getReference("messages")

        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        loadMessages()

        return view
    }

    private fun loadMessages() {
        val currentUserId = currentUser.uid
        val uniqueChats = mutableMapOf<String, Message>() // Store latest message per contact

        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()

                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)

                    if (message != null && (message.senderId == currentUserId || message.receiverId == currentUserId)) {
                        val chatPartnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId

                        // Store only the latest message per user
                        if (!uniqueChats.containsKey(chatPartnerId) || message.timestamp > (uniqueChats[chatPartnerId]?.timestamp ?: 0)) {
                            uniqueChats[chatPartnerId] = message
                        }
                    }
                }

                // Convert map values (last messages) to list and sort by timestamp (latest first)
                messageList.addAll(uniqueChats.values.sortedByDescending { it.timestamp })
                messageAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Failed to load messages", error.toException())
            }
        })
    }
}
