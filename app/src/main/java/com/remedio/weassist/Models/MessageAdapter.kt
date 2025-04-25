package com.remedio.weassist.Clients

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.MessageConversation.ImagePreviewActivity
import com.remedio.weassist.R
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private val messagesList: List<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    interface OnFileClickListener {
        fun onFileClick(message: Message)
    }

    private var fileClickListener: OnFileClickListener? = null

    fun setOnFileClickListener(listener: OnFileClickListener) {
        this.fileClickListener = listener
    }

    fun setOnFileClickListener(listener: (Message) -> Unit) {
        this.fileClickListener = object : OnFileClickListener {
            override fun onFileClick(message: Message) {
                listener(message)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_SENT_FILE = 4
        private const val VIEW_TYPE_RECEIVED_FILE = 5

        fun getMimeType(url: String): String {
            return when {
                url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) -> "image/jpeg"
                url.endsWith(".png", true) -> "image/png"
                url.endsWith(".gif", true) -> "image/gif"
                url.endsWith(".pdf", true) -> "application/pdf"
                url.endsWith(".doc", true) -> "application/msword"
                url.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                url.endsWith(".xls", true) -> "application/vnd.ms-excel"
                url.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                url.endsWith(".ppt", true) -> "application/vnd.ms-powerpoint"
                url.endsWith(".pptx", true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                url.endsWith(".txt", true) -> "text/plain"
                url.endsWith(".mp3", true) -> "audio/mpeg"
                url.endsWith(".wav", true) -> "audio/wav"
                url.endsWith(".mp4", true) -> "video/mp4"
                url.endsWith(".avi", true) -> "video/x-msvideo"
                url.endsWith(".zip", true) -> "application/zip"
                url.endsWith(".rar", true) -> "application/x-rar-compressed"
                else -> "*/*"
            }
        }

        private fun isImageFileType(fileType: String?): Boolean {
            return fileType == "image"
        }

        private fun downloadFile(context: Context, fileUrl: String, fileName: String) {
            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(fileUrl)

            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("Downloading file...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            try {
                val downloadId = downloadManager.enqueue(request)
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            Toast.makeText(context, "Download completed", Toast.LENGTH_SHORT).show()
                            context?.unregisterReceiver(this)
                        }
                    }
                }

                // For Android 13+ (API 33+), we need to specify the receiver flag
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    // For older versions, use the traditional method
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleFileClick(context: Context, message: Message) {
        message.fileUrl?.let { fileUrl ->
            when (message.fileType) {
                "image" -> {
                    // Open image preview for image files
                    val intent = Intent(context, ImagePreviewActivity::class.java).apply {
                        putExtra("image_url", fileUrl)
                    }
                    context.startActivity(intent)
                }
                "pdf" -> {
                    // Handle PDF files specifically
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(fileUrl), "application/pdf")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // If no PDF viewer is installed, download the file
                        downloadFile(context, fileUrl, message.fileName ?: "document.pdf")
                        Toast.makeText(context, "No PDF viewer found. Downloading file instead.", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    // Handle other file types generically
                    try {
                        val mimeType = getMimeType(fileUrl)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(fileUrl), mimeType)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        downloadFile(context, fileUrl, message.fileName ?: "file")
                        Toast.makeText(context, "No app found to open this file. Downloading instead.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }



    override fun getItemViewType(position: Int): Int {
        val message = messagesList[position]
        return when {
            message.senderId == "system" -> VIEW_TYPE_SYSTEM
            message.isFileMessage() && message.senderId == currentUserId -> VIEW_TYPE_SENT_FILE
            message.isFileMessage() -> VIEW_TYPE_RECEIVED_FILE
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
            VIEW_TYPE_SENT_FILE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_file_sent, parent, false)
                SentFileViewHolder(view)
            }
            VIEW_TYPE_RECEIVED_FILE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_file_received, parent, false)
                ReceivedFileViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messagesList[position]
        val date = Date(message.timestamp)
        val timeString = timeFormat.format(date)

        when (holder) {
            is SentMessageViewHolder -> {
                holder.bind(message)
                holder.tvTimestamp?.text = timeString
            }
            is ReceivedMessageViewHolder -> {
                holder.bind(message)
                holder.tvTimestamp?.text = timeString
            }
            is SystemMessageViewHolder -> holder.bind(message)
            is SentFileViewHolder -> {
                holder.bind(message, fileClickListener)
                holder.tvTimestamp?.text = timeString
            }
            is ReceivedFileViewHolder -> {
                holder.bind(message, fileClickListener)
                holder.tvTimestamp?.text = timeString
            }
        }
    }

    private fun shouldShowDateHeader(position: Int): Boolean {
        if (position == 0) return true
        val currentDate = Date(messagesList[position].timestamp)
        val previousDate = Date(messagesList[position - 1].timestamp)
        return !isSameDay(currentDate, previousDate)
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemCount(): Int = messagesList.size

    // View holder for messages sent by the current user
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val ivProfile: CircleImageView? = itemView.findViewById(R.id.profile_image)

        fun bind(message: Message) {
            tvMessage.text = message.message
            tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))

            ivProfile?.let { imageView ->
                if (!message.senderImageUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(message.senderImageUrl)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile)
                }
            }
        }
    }

    // View holder for messages received from other users
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        private val tvSenderName: TextView? = itemView.findViewById(R.id.sender_name)
        val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val ivProfile: CircleImageView? = itemView.findViewById(R.id.profile_image)

        fun bind(message: Message) {
            tvMessage.text = message.message
            tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))

            tvSenderName?.let { nameView ->
                if (message.senderName != null) {
                    nameView.text = message.senderName
                    nameView.visibility = View.VISIBLE
                } else {
                    nameView.visibility = View.GONE
                }
            }

            ivProfile?.let { imageView ->
                if (!message.senderImageUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(message.senderImageUrl)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile)
                }
            }
        }
    }

    // View holder for system messages
    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.system_message_text)

        fun bind(message: Message) {
            tvMessage.text = message.message
        }
    }

    // View holder for files sent by current user
    class SentFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.file_name)
        private val ivFileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        private val ivFilePreview: ImageView? = itemView.findViewById(R.id.file_preview)
        val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val ivProfile: CircleImageView? = itemView.findViewById(R.id.profile_image)

        fun bind(message: Message, fileClickListener: OnFileClickListener? = null) {
            tvFileName.text = message.fileName ?: "File"
            tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))

            val iconRes = when (message.fileType) {
                "image" -> R.drawable.ic_image
                "pdf" -> R.drawable.ic_pdf
                "video" -> R.drawable.ic_video
                else -> R.drawable.ic_file
            }
            ivFileIcon.setImageResource(iconRes)

            // Hide preview for non-image files
            if (message.fileType == "image" && ivFilePreview != null) {
                ivFilePreview.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.fileUrl)
                    .placeholder(R.drawable.ic_image)
                    .into(ivFilePreview)
            } else {
                ivFilePreview?.visibility = View.GONE
            }

            ivProfile?.let { imageView ->
                if (!message.senderImageUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(message.senderImageUrl)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile)
                }
            }

            itemView.setOnClickListener {
                fileClickListener?.onFileClick(message) ?: run {
                    // Fallback if no listener is set
                    val adapter = (itemView.context as? ChatActivity)?.messagesAdapter
                    adapter?.handleFileClick(itemView.context, message)
                }
            }
        }
    }

    // View holder for files received from other users
    class ReceivedFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.file_name)
        private val tvSenderName: TextView? = itemView.findViewById(R.id.sender_name)
        private val ivFileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        private val ivFilePreview: ImageView? = itemView.findViewById(R.id.file_preview)
        val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val ivProfile: CircleImageView? = itemView.findViewById(R.id.profile_image)

        fun bind(message: Message, fileClickListener: OnFileClickListener? = null) {
            tvFileName.text = message.fileName ?: "File"
            tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))

            tvSenderName?.let { nameView ->
                if (message.senderName != null) {
                    nameView.text = message.senderName
                    nameView.visibility = View.VISIBLE
                } else {
                    nameView.visibility = View.GONE
                }
            }

            val iconRes = when (message.fileType) {
                "image" -> R.drawable.ic_image
                "pdf" -> R.drawable.ic_pdf
                "video" -> R.drawable.ic_video
                else -> R.drawable.ic_file
            }
            ivFileIcon.setImageResource(iconRes)

            // Hide preview for non-image files
            if (message.fileType == "image" && ivFilePreview != null) {
                ivFilePreview.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.fileUrl)
                    .placeholder(R.drawable.ic_image)
                    .into(ivFilePreview)
            } else {
                ivFilePreview?.visibility = View.GONE
            }

            ivProfile?.let { imageView ->
                if (!message.senderImageUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(message.senderImageUrl)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile)
                }
            }

            itemView.setOnClickListener {
                fileClickListener?.onFileClick(message) ?: run {
                    // Fallback if no listener is set
                    val adapter = (itemView.context as? ChatActivity)?.messagesAdapter
                    adapter?.handleFileClick(itemView.context, message)
                }
            }
        }
    }
}