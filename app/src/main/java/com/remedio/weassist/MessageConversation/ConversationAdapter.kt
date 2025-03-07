package com.remedio.weassist.MessageConversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class ConversationAdapter(
    private val conversationList: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit,
    private val currentUserId: String? = null  // Add current user ID to determine the role
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

        holder.itemView.setOnClickListener {
            onItemClick(conversation)
        }
    }

    override fun getItemCount(): Int = conversationList.size
}