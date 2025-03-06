package com.remedio.weassist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConversationAdapter(
    private val conversationList: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit
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
        holder.nameTextView.text = conversation.secretaryName  // Replace with Secretary's name lookup
        holder.lastMessageTextView.text = conversation.lastMessage
        holder.unreadCountTextView.text = conversation.unreadCount.toString()

        holder.itemView.setOnClickListener { onItemClick(conversation) }
    }

    override fun getItemCount(): Int = conversationList.size
}
