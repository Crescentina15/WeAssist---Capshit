package com.remedio.weassist.Clients

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.MessageConversation.ImagePreviewActivity
import com.remedio.weassist.R
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.io.FileOutputStream
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

        fun getMimeTypeFromExtension(extension: String): String {
            return when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "pdf" -> "application/pdf"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "txt" -> "text/plain"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                else -> "*/*"
            }
        }

        fun getMimeType(url: String, fileName: String? = null, fileType: String? = null): String {
            // First try to get from fileName if provided
            if (fileName != null && fileName.contains('.')) {
                val fileExtension = fileName.substringAfterLast('.').lowercase()
                val mimeFromExt = getMimeTypeFromExtension(fileExtension)
                if (mimeFromExt != "*/*") return mimeFromExt
            }

            // Next, try to determine from fileType
            when (fileType?.lowercase()) {
                "image" -> return "image/jpeg"
                "pdf" -> return "application/pdf"
                "video" -> return "video/mp4"
            }

            // First try to get from URL extension
            val extension = url.substringAfterLast('.').lowercase()
            return when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "pdf" -> "application/pdf"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "txt" -> "text/plain"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                else -> {
                    // If extension doesn't help, check the provided fileType
                    when (fileType?.lowercase()) {
                        "image" -> "image/*"
                        "pdf" -> "application/pdf"
                        "video" -> "video/*"
                        else -> "*/*"
                    }
                }
            }
        }

        private fun isImageFileType(fileType: String?): Boolean {
            return fileType == "image"
        }

        private fun getFilePathFromContentUri(context: Context, contentUri: Uri): String? {
            try {
                // Try to get the direct path if possible
                val filePathColumn = arrayOf(MediaStore.MediaColumns.DATA)
                val cursor =
                    context.contentResolver.query(contentUri, filePathColumn, null, null, null)

                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(filePathColumn[0])
                        if (columnIndex != -1) {
                            return it.getString(columnIndex)
                        }
                    }
                }

                // If direct path not found, copy to a temp file and return its path
                context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                    val fileName =
                        contentUri.lastPathSegment ?: "temp_${System.currentTimeMillis()}"
                    val tempFile = File(context.cacheDir, fileName)
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        outputStream.flush()
                    }
                    return tempFile.absolutePath
                }
            } catch (e: Exception) {
                Log.e("DownloadFile", "Error resolving file path: ${e.message}")
            }
            return null
        }

        private fun downloadFile(
            context: Context,
            fileUrl: String,
            fileName: String,
            fileType: String? = null
        ) {
            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(fileUrl)

            // Ensure we have the correct extension
            val extension = fileUrl.substringAfterLast('.', "").takeIf { it.length in 1..5 } ?: ""
            val finalFileName = if (fileName.contains('.')) {
                fileName
            } else if (extension.isNotEmpty()) {
                "$fileName.$extension"
            } else {
                // Add extension based on fileType if no extension in URL
                when (fileType?.lowercase()) {
                    "image" -> "$fileName.jpg"
                    "pdf" -> "$fileName.pdf"
                    "video" -> "$fileName.mp4"
                    else -> fileName
                }
            }

            // Determine the correct MIME type
            val mimeType = when {
                fileType == "pdf" || finalFileName.endsWith(".pdf", true) -> "application/pdf"
                fileType == "image" || finalFileName.lowercase()
                    .matches(Regex(".*\\.(jpg|jpeg|png|gif)$")) ->
                    when {
                        finalFileName.lowercase().endsWith(".png") -> "image/png"
                        finalFileName.lowercase().endsWith(".gif") -> "image/gif"
                        else -> "image/jpeg"
                    }

                fileType == "video" || finalFileName.lowercase()
                    .matches(Regex(".*\\.(mp4|avi|mov|wmv)$")) ->
                    when {
                        finalFileName.lowercase().endsWith(".avi") -> "video/x-msvideo"
                        finalFileName.lowercase().endsWith(".wmv") -> "video/x-ms-wmv"
                        finalFileName.lowercase().endsWith(".mov") -> "video/quicktime"
                        else -> "video/mp4"
                    }

                finalFileName.lowercase().endsWith(".doc") -> "application/msword"
                finalFileName.lowercase()
                    .endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

                finalFileName.lowercase().endsWith(".xls") -> "application/vnd.ms-excel"
                finalFileName.lowercase()
                    .endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

                finalFileName.lowercase().endsWith(".txt") -> "text/plain"
                else -> "*/*"
            }

            Log.d("DownloadFile", "Downloading file: $finalFileName with MIME type: $mimeType")

            // Create the download directory if it doesn't exist
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val request = DownloadManager.Request(uri)
                .setTitle(finalFileName)
                .setDescription("Downloading file...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
                .setMimeType(mimeType) // Explicitly set the MIME type
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            try {
                val downloadId = downloadManager.enqueue(request)
                Toast.makeText(context, "Download started: $finalFileName", Toast.LENGTH_SHORT)
                    .show()

                // Register receiver to handle download completion
                val receiver = object : BroadcastReceiver() {
                    @SuppressLint("Range")
                    override fun onReceive(contextRcv: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = downloadManager.query(query)

                            if (cursor.moveToFirst()) {
                                val status =
                                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))

                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    val localUriString =
                                        cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                    val localUri = Uri.parse(localUriString)

                                    Log.d("DownloadFile", "Download complete. Local URI: $localUri")
                                    Toast.makeText(
                                        contextRcv,
                                        "Download completed: $finalFileName",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    // Get the actual file path
                                    try {
                                        if (contextRcv == null) return

                                        // Convert content:// or file:// URI to File
                                        val filePath = when {
                                            localUri.scheme == "content" -> {
                                                // For content:// URIs
                                                getFilePathFromContentUri(contextRcv, localUri)
                                            }

                                            localUri.scheme == "file" -> {
                                                // For file:// URIs
                                                localUri.path
                                            }

                                            else -> null
                                        }

                                        if (filePath == null) {
                                            Log.e("DownloadFile", "Could not resolve file path")
                                            Toast.makeText(
                                                contextRcv,
                                                "Could not open file",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return
                                        }

                                        val file = File(filePath)
                                        if (!file.exists()) {
                                            Log.e("DownloadFile", "File doesn't exist: $filePath")
                                            Toast.makeText(
                                                contextRcv,
                                                "Downloaded file not found",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return
                                        }

                                        // Use FileProvider to get content URI for the file
                                        val contentUri = FileProvider.getUriForFile(
                                            contextRcv,
                                            "${contextRcv.packageName}.fileprovider",
                                            file
                                        )

                                        Log.d(
                                            "DownloadFile",
                                            "Opening file with MIME type: $mimeType"
                                        )
                                        Log.d("DownloadFile", "Content URI: $contentUri")

                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(contentUri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }

                                        try {
                                            contextRcv.startActivity(openIntent)
                                        } catch (e: ActivityNotFoundException) {
                                            Log.e(
                                                "DownloadFile",
                                                "No app can handle this file type",
                                                e
                                            )
                                            Toast.makeText(
                                                contextRcv,
                                                "No app found to open this file type. File saved to Downloads folder.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DownloadFile", "Error opening file", e)
                                        Toast.makeText(
                                            contextRcv,
                                            "Error opening file: ${e.message}. File saved to Downloads folder.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    val reason =
                                        cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                                    Log.e("DownloadFile", "Download failed with reason: $reason")
                                    Toast.makeText(
                                        contextRcv,
                                        "Download failed: $reason",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            cursor.close()
                            contextRcv?.unregisterReceiver(this)
                        }
                    }
                }

                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                Log.e("DownloadFile", "Download setup failed", e)
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun handleFileClick(context: Context, message: Message) {
        message.fileUrl?.let { fileUrl ->
            // Get a proper file name, ensuring it has an extension
            val fileNameBase = message.fileName
                ?: fileUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotEmpty() }
                ?: "file_${System.currentTimeMillis()}"

            // Make sure we have a file extension
            val fileExtension = when {
                fileNameBase.contains('.') -> ""
                message.fileType == "image" -> ".jpg"
                message.fileType == "pdf" -> ".pdf"
                message.fileType == "video" -> ".mp4"
                else -> ".bin"
            }

            val fileName =
                if (fileNameBase.contains('.')) fileNameBase else "$fileNameBase$fileExtension"

            Log.d(
                "HandleFileClick",
                "File URL: $fileUrl, Name: $fileName, Type: ${message.fileType}"
            )

            when {
                message.fileType == "image" -> {
                    try {
                        val intent = Intent(context, ImagePreviewActivity::class.java).apply {
                            putExtra("image_url", fileUrl)
                            putExtra("image_name", fileName)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("HandleFileClick", "Error opening image preview", e)
                        // Fallback to download if preview fails
                        downloadFile(context, fileUrl, fileName, message.fileType)
                    }
                }

                message.fileType == "pdf" || fileName.endsWith(".pdf", true) -> {
                    Log.d("HandleFileClick", "Downloading PDF file directly")
                    // Always download PDF files with attachment flag instead of trying to view them
                    val downloadUrl = if (fileUrl.contains("?")) {
                        "$fileUrl&fl_attachment=true"
                    } else {
                        "$fileUrl?fl_attachment=true"
                    }
                    downloadFile(context, downloadUrl, fileName, "pdf")
                    Toast.makeText(context, "Downloading PDF: $fileName", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    Log.d("HandleFileClick", "Downloading general file")
                    // Add fl_attachment parameter to all file downloads for consistency
                    val downloadUrl = if (fileUrl.contains("?")) {
                        "$fileUrl&fl_attachment=true"
                    } else {
                        "$fileUrl?fl_attachment=true"
                    }
                    downloadFile(context, downloadUrl, fileName, message.fileType)
                    Toast.makeText(context, "Downloading file: $fileName", Toast.LENGTH_SHORT)
                        .show()
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

            // Add long-press listener for image download
            if (message.fileType == "image") {
                itemView.setOnLongClickListener {
                    message.fileUrl?.let { fileUrl ->
                        val fileName = message.fileName ?: fileUrl.substringAfterLast('/')
                        downloadFile(
                            itemView.context,
                            fileUrl,
                            fileName,
                            message.fileType
                        )
                        Toast.makeText(itemView.context, "Downloading image...", Toast.LENGTH_SHORT)
                            .show()
                    }
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
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

            // Add long-press listener for image download
            if (message.fileType == "image") {
                itemView.setOnLongClickListener {
                    message.fileUrl?.let { fileUrl ->
                        val fileName = message.fileName ?: fileUrl.substringAfterLast('/')
                        downloadFile(
                            itemView.context,
                            fileUrl,
                            fileName,
                            message.fileType
                        )
                        Toast.makeText(itemView.context, "Downloading image...", Toast.LENGTH_SHORT)
                            .show()
                    }
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }
}