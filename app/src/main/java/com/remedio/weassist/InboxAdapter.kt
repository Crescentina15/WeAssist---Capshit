import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.InboxItem
import com.remedio.weassist.R

class InboxAdapter(private val inboxList: List<InboxItem>) : RecyclerView.Adapter<InboxAdapter.InboxViewHolder>() {

    class InboxViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //val profileImage: ImageView = view.findViewById(R.id.profileImage)
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
    }

    override fun getItemCount(): Int = inboxList.size
}
