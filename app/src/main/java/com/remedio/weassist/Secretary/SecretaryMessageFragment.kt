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
import com.remedio.weassist.InboxItem
import com.remedio.weassist.R
import com.remedio.weassist.SecretaryInboxAdapter

class SecretaryMessageFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var inboxAdapter: SecretaryInboxAdapter
    private val inboxList = mutableListOf<InboxItem>()
    private lateinit var messagesRef: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_secretary_message, container, false)

        recyclerView = view.findViewById(R.id.inbox_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        currentUser = FirebaseAuth.getInstance().currentUser!!
        messagesRef = FirebaseDatabase.getInstance().getReference("messages")
        usersRef = FirebaseDatabase.getInstance().getReference("Users") // Confirm correct path

        inboxAdapter = SecretaryInboxAdapter(inboxList) { selectedInbox ->
            // Handle click event - Open chat
        }
        recyclerView.adapter = inboxAdapter

        loadSecretaryInbox()

        return view
    }

    private fun loadSecretaryInbox() {
        val currentUserId = currentUser.uid
        Log.d("FirebaseCheck", "Current User ID: $currentUserId") // Debugging
        val uniqueChats = mutableMapOf<String, InboxItem>()

        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inboxList.clear()
                val clientIds = mutableSetOf<String>()

                Log.d("FirebaseCheck", "Messages snapshot retrieved: ${snapshot.exists()}")

                // Step 1: Collect all client IDs that the secretary has messages with
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)

                    Log.d("FirebaseCheck", "Message Retrieved: ${messageSnapshot.value}")

                    if (message != null && (message.senderId == currentUserId || message.receiverId == currentUserId)) {
                        val chatPartnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId
                        clientIds.add(chatPartnerId)
                    }
                }

                Log.d("FirebaseCheck", "Client IDs found: $clientIds")

                // Step 2: Fetch user names from Firebase
                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(userSnapshot: DataSnapshot) {
                        for (chatPartnerId in clientIds) {
                            val firstName = userSnapshot.child(chatPartnerId).child("firstName").value?.toString() ?: ""
                            val lastName = userSnapshot.child(chatPartnerId).child("lastName").value?.toString() ?: ""
                            val clientName = if (firstName.isNotEmpty() && lastName.isNotEmpty()) "$firstName $lastName" else "Unknown"

                            Log.d("FirebaseCheck", "Fetched client name: $clientName for ID: $chatPartnerId")

                            // Step 3: Find the latest message for this chat partner
                            for (messageSnapshot in snapshot.children) {
                                val message = messageSnapshot.getValue(Message::class.java)
                                if (message != null && (message.senderId == currentUserId || message.receiverId == currentUserId)) {
                                    val partnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId

                                    if (partnerId == chatPartnerId) {
                                        val unreadCount = messageSnapshot.child("unreadCount").getValue(Int::class.java) ?: 0
                                        val messageTimestamp = message.timestamp.toString().toLongOrNull() ?: 0L

                                        // Step 4: Store only the latest message per chat
                                        if (!uniqueChats.containsKey(chatPartnerId) || messageTimestamp > (uniqueChats[chatPartnerId]?.timestamp?.toLongOrNull() ?: 0L)) {
                                            uniqueChats[chatPartnerId] = InboxItem(
                                                chatPartnerId, // Generic chat partner ID
                                                clientName, // Fetch the correct client name
                                                message.message,
                                                messageTimestamp.toString(),
                                                unreadCount
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Step 5: Update UI with sorted inbox list
                        inboxList.clear()
                        inboxList.addAll(uniqueChats.values.sortedByDescending { it.timestamp })

                        requireActivity().runOnUiThread {
                            inboxAdapter.notifyDataSetChanged()
                        }

                        Log.d("FirebaseCheck", "Inbox list updated: ${inboxList.size} items")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FirebaseError", "Failed to load users", error.toException())
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Failed to load messages", error.toException())
            }
        })
    }
}
