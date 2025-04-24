package com.remedio.weassist.Clients

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String? = null,
    val senderImageUrl: String? = null,
    val fileUrl: String? = null,          // URL of the uploaded file in Cloudinary
    val fileType: String? = null,         // Type of file (e.g., "image", "pdf", "video")
    val fileName: String? = null,         // Original filename
    val fileSize: Long? = null            // File size in bytes (optional)
) {
    // Helper function to check if this message contains a file
    fun isFileMessage(): Boolean {
        return !fileUrl.isNullOrEmpty()
    }

    // Helper function to get the appropriate display text for the message
    fun getDisplayText(): String {
        return if (isFileMessage()) {
            fileName ?: "File"
        } else {
            message
        }
    }
}