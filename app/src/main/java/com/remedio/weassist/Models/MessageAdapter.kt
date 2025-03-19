package com.remedio.weassist.Clients

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messagesList: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        return if (messagesList[position].senderId == currentUserId) {
            R.layout.item_message_sent // If the message is sent by the current user
        } else {
            R.layout.item_message_received // If the message is received
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messagesList[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messagesList.size

    class MessageViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        //private val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val tvSenderName: TextView? = itemView.findViewById(R.id.sender_name)

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(message: Message) {
            tvMessage.text = message.message

            // Set timestamp if the view has it
            //tvTimestamp?.text = dateFormat.format(Date(message.timestamp))

            // Set sender name for received messages if the view supports it
            if (viewType == R.layout.item_message_received && tvSenderName != null) {
                // If we have a sender name in the message, use it
                val senderName = message.senderName ?: "Unknown"
                tvSenderName.text = senderName
                tvSenderName.visibility = View.VISIBLE
            }
        }
    }
}