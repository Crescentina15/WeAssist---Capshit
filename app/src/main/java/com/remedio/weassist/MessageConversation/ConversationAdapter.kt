package com.remedio.weassist.MessageConversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R

class ConversationAdapter(
    private val conversationList: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit,
    private val currentUserId: String? = null,  // Add current user ID to determine the role
    private val onLongClickListener: ((View, Int) -> Boolean)? = null // Optional long click listener
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.secretary_name)
        val lastMessageTextView: TextView = view.findViewById(R.id.last_message)
        val unreadCountTextView: TextView = view.findViewById(R.id.unread_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversationList[position]

        // Determine if current user is a secretary (by checking if they're conversing with client)
        val isSecretaryView = currentUserId != null && conversation.clientId != currentUserId

        // Display the appropriate name based on user role
        if (isSecretaryView) {
            // Secretary viewing client conversation
            holder.nameTextView.text = conversation.clientName
        } else {
            // Client viewing secretary conversation
            holder.nameTextView.text = conversation.secretaryName
        }

        holder.lastMessageTextView.text = conversation.lastMessage
        holder.unreadCountTextView.text = conversation.unreadCount.toString()
        holder.unreadCountTextView.visibility = if (conversation.unreadCount > 0) View.VISIBLE else View.GONE

        // Check for forwarded conversation status
        checkIfConversationIsActive(conversation.conversationId) { isActive, isHandledByLawyer ->
            if (!isActive && isSecretaryView) {
                // This conversation has been forwarded and secretary is inactive
                holder.itemView.alpha = 0.5f  // Dim the conversation
                holder.lastMessageTextView.text = "[Forwarded to lawyer] " + holder.lastMessageTextView.text

                // We could also add a visual indicator here like a forwarded icon
                // holder.forwardedIndicator.visibility = View.VISIBLE (if you add this view to your layout)
            } else {
                holder.itemView.alpha = 1.0f
            }

            // Set click listener - handle differently for inactive conversations
            holder.itemView.setOnClickListener {
                if (!isActive && isSecretaryView) {
                    // Show a toast or dialog explaining the conversation is now handled by a lawyer
                    val context = holder.itemView.context
                    Toast.makeText(
                        context,
                        "This conversation has been forwarded to a lawyer and is now read-only.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Still allow viewing the conversation, but perhaps in a read-only mode
                    onItemClick(conversation)
                } else {
                    // Normal click behavior for active conversations
                    onItemClick(conversation)
                }
            }
        }

        // Only add long press listener if it's provided AND this is a secretary view
        // AND the conversation is still active (not forwarded)
        if (onLongClickListener != null && isSecretaryView) {
            checkIfConversationIsActive(conversation.conversationId) { isActive, _ ->
                if (isActive) {
                    holder.itemView.setOnLongClickListener { view ->
                        onLongClickListener.invoke(view, position)
                    }
                } else {
                    // No long press for forwarded conversations
                    holder.itemView.setOnLongClickListener(null)
                }
            }
        }
    }

    // Helper method to check conversation status
    private fun checkIfConversationIsActive(conversationId: String, callback: (Boolean, Boolean) -> Unit) {
        val database = FirebaseDatabase.getInstance().reference
        database.child("conversations").child(conversationId).get()
            .addOnSuccessListener { snapshot ->
                val isActive = !(snapshot.child("secretaryActive").exists() &&
                        snapshot.child("secretaryActive").getValue(Boolean::class.java) == false)

                val isHandledByLawyer = snapshot.child("handledByLawyer").exists() &&
                        snapshot.child("handledByLawyer").getValue(Boolean::class.java) == true

                callback(isActive, isHandledByLawyer)
            }
            .addOnFailureListener {
                // Default to active if we can't determine
                callback(true, false)
            }
    }


    override fun getItemCount(): Int = conversationList.size
}