package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.MessageConversation.Conversation
import com.remedio.weassist.MessageConversation.ConversationAdapter
import com.remedio.weassist.R

class ClientMessageFragment : Fragment() {
    private lateinit var database: DatabaseReference
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var conversationsAdapter: ConversationAdapter
    private val conversationList = mutableListOf<Conversation>()
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fetchConversationsDebug()
        val view = inflater.inflate(R.layout.fragment_client_message, container, false)
        conversationsRecyclerView = view.findViewById(R.id.inbox_recycler_view)
        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // In ClientMessageFragment
        conversationsAdapter = ConversationAdapter(
            conversationList,
            { conversation -> openChatActivity(conversation.secretaryId) },
            currentUserId  // Pass current user ID (client)
        )
        conversationsRecyclerView.adapter = conversationsAdapter

        database = FirebaseDatabase.getInstance().reference

        checkAuthStatus() // 🔹 Check if the user is logged in

        fetchConversations()

        return view
    }

    private fun checkAuthStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("FirebaseAuth", "User is NOT authenticated!")
        } else {
            Log.d("FirebaseAuth", "Authenticated as: ${user.uid}")
        }
        Log.d("AuthCheck", "Current User ID: $currentUserId")
    }
    private fun fetchConversationsDebug() {
        val database = FirebaseDatabase.getInstance().reference
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId == null) {
            Log.e("FirebaseDB", "Current User ID is NULL!")
            return
        }

        database.child("conversations").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Log.d("FirebaseDB", "Fetched Data: ${snapshot.value}")
                    for (conversationSnapshot in snapshot.children) {
                        val conversationId = conversationSnapshot.key
                        Log.d("FirebaseDB", "Conversation ID: $conversationId")

                        val participants = conversationSnapshot.child("participantIds").children
                        for (participant in participants) {
                            Log.d("FirebaseDB", "Participant ID: ${participant.key}")
                        }

                        // Check if the current user is a participant
                        if (conversationSnapshot.child("participantIds").hasChild(currentUserId)) {
                            Log.d("FirebaseDB", "User $currentUserId is a participant in conversation: $conversationId")
                        } else {
                            Log.e("FirebaseDB", "User $currentUserId is NOT a participant in conversation: $conversationId")
                        }
                    }
                } else {
                    Log.e("FirebaseDB", "No conversations found in the database.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseDB", "Error: ${e.message}")
            }
    }


    private fun fetchConversations() {
        if (currentUserId == null) return

        val conversationsRef = database.child("conversations")
        conversationsRef.orderByChild("participantIds/$currentUserId").equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    conversationList.clear()

                    if (!snapshot.exists()) {
                        Log.e("FirebaseDB", "No conversations found for user: $currentUserId")
                        conversationsAdapter.notifyDataSetChanged()
                        return
                    }

                    val tempConversationList = mutableListOf<Conversation>()
                    var fetchCount = 0

                    for (conversationSnapshot in snapshot.children) {
                        val conversationId = conversationSnapshot.key ?: continue
                        val otherParticipantId = conversationSnapshot.child("participantIds")
                            .children.firstOrNull { it.key != currentUserId }?.key ?: continue

                        if (!conversationSnapshot.child("participantIds").hasChild(currentUserId!!)) {
                            Log.e("FirebaseDB", "User $currentUserId is NOT a participant in $conversationId")
                            continue
                        }

                        val lastMessage = conversationSnapshot.child("messages").children.lastOrNull()
                            ?.child("message")?.getValue(String::class.java) ?: "No messages yet"

                        val unreadCount = conversationSnapshot.child("unreadMessages/$currentUserId")
                            .getValue(Int::class.java) ?: 0

                        fetchCount++
                        fetchSecretaryName(otherParticipantId) { secretaryName ->
                            val conversation = Conversation(
                                conversationId = conversationId,
                                clientId = currentUserId ?: "",
                                secretaryId = otherParticipantId,
                                clientName = "", // Empty for client-side view
                                secretaryName = secretaryName,
                                lastMessage = lastMessage,
                                unreadCount = unreadCount
                            )

                            tempConversationList.add(conversation)
                            fetchCount--

                            // Only update the adapter once all secretary names are fetched
                            if (fetchCount == 0) {
                                conversationList.clear()
                                conversationList.addAll(tempConversationList)
                                conversationsAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ClientMessagesFragment", "Error fetching conversations: ${error.message}")
                }
            })
    }


    private fun fetchSecretaryName(secretaryId: String, callback: (String) -> Unit) {
        val secretariesRef = database.child("secretaries").child(secretaryId).child("name")
        secretariesRef.get().addOnSuccessListener { snapshot ->
            val secretaryName = snapshot.value?.toString() ?: "Unknown"
            callback(secretaryName)
        }.addOnFailureListener {
            callback("Unknown")
        }
    }



    private fun openChatActivity(secretaryId: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("SECRETARY_ID", secretaryId)
        startActivity(intent)
    }
}
