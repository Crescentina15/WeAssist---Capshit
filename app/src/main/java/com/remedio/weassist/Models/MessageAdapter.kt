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
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    // Define view type constants
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
    }

    override fun getItemViewType(position: Int): Int {
        val message = messagesList[position]
        return when {
            message.senderId == "system" -> VIEW_TYPE_SYSTEM
            message.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            VIEW_TYPE_SYSTEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_system, parent, false)
                SystemMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messagesList[position]

        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messagesList.size

    // View holder for messages sent by the current user
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        //private val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)

        fun bind(message: Message) {
            tvMessage.text = message.message
            //tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        }
    }

    // View holder for messages received from other users
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        //private val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val tvSenderName: TextView? = itemView.findViewById(R.id.sender_name)

        fun bind(message: Message) {
            tvMessage.text = message.message
            //tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(message.timestamp))

            // Set sender name if available
            val senderName = message.senderName ?: "Unknown"
            tvSenderName?.text = senderName
            tvSenderName?.visibility = View.VISIBLE
        }
    }

    // View holder for system messages
    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.system_message_text)

        fun bind(message: Message) {
            tvMessage.text = message.message
        }
    }
}