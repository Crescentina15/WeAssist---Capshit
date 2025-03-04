package com.remedio.weassist


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R
import com.remedio.weassist.InboxItem

class SecretaryInboxAdapter(
    private val inboxList: List<InboxItem>,
    private val onItemClick: (InboxItem) -> Unit
) : RecyclerView.Adapter<SecretaryInboxAdapter.SecretaryInboxViewHolder>() {

    class SecretaryInboxViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val clientName: TextView = view.findViewById(R.id.client_name)
        val lastMessage: TextView = view.findViewById(R.id.last_message)
        val unreadCount: TextView = view.findViewById(R.id.unread_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretaryInboxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_secretary_inbox, parent, false)
        return SecretaryInboxViewHolder(view)
    }

    override fun onBindViewHolder(holder: SecretaryInboxViewHolder, position: Int) {
        val inboxItem = inboxList[position]
        holder.clientName.text = inboxItem.chatPartnerName
        holder.lastMessage.text = inboxItem.lastMessage
        holder.unreadCount.text = if (inboxItem.unreadCount > 0) inboxItem.unreadCount.toString() else ""

        // Click event to open chat
        holder.itemView.setOnClickListener { onItemClick(inboxItem) }
    }

    override fun getItemCount() = inboxList.size
}
