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

        // Fetch all secretaries first
        secretariesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(secSnapshot: DataSnapshot) {
                val secretariesMap = mutableMapOf<String, String>() // Stores secretaryId -> name

                for (secretary in secSnapshot.children) {
                    val secretaryId = secretary.key ?: continue
                    val name = secretary.child("name").getValue(String::class.java) ?: "Unknown"
                    secretariesMap[secretaryId] = name
                }

                // Now fetch messages in real-time
                messagesRef.addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                        processMessage(snapshot, currentUserId, secretariesMap)
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                        processMessage(snapshot, currentUserId, secretariesMap)
                    }

                    override fun onChildRemoved(snapshot: DataSnapshot) {
                        removeMessage(snapshot, currentUserId)
                    }

                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FirebaseError", "Failed to load messages", error.toException())
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Failed to load secretaries", error.toException())
            }
        })
    }

    private fun processMessage(snapshot: DataSnapshot, currentUserId: String, secretariesMap: Map<String, String>) {
        val message = snapshot.getValue(Message::class.java) ?: return
        if (message.senderId != currentUserId && message.receiverId != currentUserId) return

        val chatPartnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId
        val secretaryName = secretariesMap[chatPartnerId] ?: return // Ignore if not a secretary

        val messageTimestamp = message.timestamp.toString().toLongOrNull() ?: 0L
        val unreadCount = snapshot.child("unreadCount").getValue(Int::class.java) ?: 0

        // Find existing chat or add new one
        val existingIndex = inboxList.indexOfFirst { it.chatPartnerId == chatPartnerId }
        if (existingIndex != -1) {
            inboxList[existingIndex] = InboxItem(chatPartnerId, secretaryName, message.message, messageTimestamp.toString(), unreadCount)
        } else {
            inboxList.add(InboxItem(chatPartnerId, secretaryName, message.message, messageTimestamp.toString(), unreadCount))
        }

        // Sort and update UI
        inboxList.sortByDescending { it.timestamp.toLongOrNull() ?: 0L }
        inboxAdapter.notifyDataSetChanged()
    }

    private fun removeMessage(snapshot: DataSnapshot, currentUserId: String) {
        val message = snapshot.getValue(Message::class.java) ?: return
        if (message.senderId != currentUserId && message.receiverId != currentUserId) return

        val chatPartnerId = if (message.senderId == currentUserId) message.receiverId else message.senderId
        inboxList.removeAll { it.chatPartnerId == chatPartnerId }

        inboxAdapter.notifyDataSetChanged()
    }
}
