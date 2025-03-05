import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.InboxItem
import com.remedio.weassist.R

class InboxAdapter(
    private val inboxList: List<InboxItem>,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<InboxAdapter.InboxViewHolder>() {

    // Interface for handling item clicks
    interface OnItemClickListener {
        fun onItemClick(inboxItem: InboxItem)
    }

    class InboxViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.nameTextView)
        val messageTextView: TextView = view.findViewById(R.id.messageTextView)
        val timestampTextView: TextView = view.findViewById(R.id.timestampTextView)
        val unreadBadge: TextView = view.findViewById(R.id.unreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InboxViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inbox, parent, false)
        return InboxViewHolder(view)
    }

    override fun onBindViewHolder(holder: InboxViewHolder, position: Int) {
        val inboxItem = inboxList[position]

        holder.nameTextView.text = inboxItem.chatPartnerName
        holder.messageTextView.text = inboxItem.lastMessage
        holder.timestampTextView.text = inboxItem.timestamp

        // Show unread badge only if there are unread messages
        if (inboxItem.unreadCount > 0) {
            holder.unreadBadge.text = inboxItem.unreadCount.toString()
            holder.unreadBadge.visibility = View.VISIBLE
        } else {
            holder.unreadBadge.visibility = View.GONE
        }

        // Set click listener for the entire item view
        holder.itemView.setOnClickListener {
            onItemClickListener.onItemClick(inboxItem)
        }
    }

    override fun getItemCount(): Int = inboxList.size
}