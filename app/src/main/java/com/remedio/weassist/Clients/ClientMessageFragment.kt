package com.remedio.weassist.Clients

import InboxAdapter
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
import com.remedio.weassist.InboxItem
import com.remedio.weassist.R

class ClientMessageFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var inboxAdapter: InboxAdapter
    private val inboxList = mutableListOf<InboxItem>()
    private lateinit var messagesRef: DatabaseReference
    private lateinit var secretariesRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_message, container, false)

        recyclerView = view.findViewById(R.id.inbox_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        currentUser = FirebaseAuth.getInstance().currentUser!!
        messagesRef = FirebaseDatabase.getInstance().getReference("messages")
        secretariesRef = FirebaseDatabase.getInstance().getReference("secretaries")

        inboxAdapter = InboxAdapter(inboxList)
        recyclerView.adapter = inboxAdapter

        loadClientInbox()

        return view
    }

    private fun loadClientInbox() {
        val currentUserId = currentUser.uid
        val uniqueChats = mutableMapOf<String, InboxItem>()

        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inboxList.clear()

                val secretaryIds = mutableSetOf<String>()

                // Collect all secretary IDs
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)
                    if (message != null && (message.senderId == currentUserId || message.receiverId == currentUserId)) {
                        val chatPartnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId
                        secretaryIds.add(chatPartnerId)
                    }
                }

                // Fetch all secretaries in one call
                secretariesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(secSnapshot: DataSnapshot) {
                        for (messageSnapshot in snapshot.children) {
                            val message = messageSnapshot.getValue(Message::class.java)
                            if (message != null && (message.senderId == currentUserId || message.receiverId == currentUserId)) {
                                val chatPartnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId

                                if (secSnapshot.child(chatPartnerId).exists()) {
                                    val secretaryName = secSnapshot.child(chatPartnerId).child("name").value.toString()
                                    val unreadCount = messageSnapshot.child("unreadCount").getValue(Int::class.java) ?: 0

                                    val messageTimestamp = message.timestamp.toString().toLongOrNull() ?: 0L

                                    if (!uniqueChats.containsKey(chatPartnerId) || messageTimestamp > (uniqueChats[chatPartnerId]?.timestamp?.toLongOrNull() ?: 0L)) {
                                        uniqueChats[chatPartnerId] = InboxItem(
                                            chatPartnerId, secretaryName, message.message,
                                            messageTimestamp.toString(), unreadCount
                                        )
                                    }
                                }
                            }
                        }

                        // Update inbox list and UI after processing all messages
                        inboxList.clear()
                        inboxList.addAll(uniqueChats.values.sortedByDescending { it.timestamp })
                        inboxAdapter.notifyDataSetChanged()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FirebaseError", "Failed to load secretaries", error.toException())
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Failed to load messages", error.toException())
            }
        })
    }


}

