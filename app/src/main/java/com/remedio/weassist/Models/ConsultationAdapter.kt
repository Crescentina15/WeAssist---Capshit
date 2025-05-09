package com.remedio.weassist.Models

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.remedio.weassist.R

class ConsultationAdapter(private val consultationList: ArrayList<Consultation>) :
    RecyclerView.Adapter<ConsultationAdapter.ViewHolder>() {

    var onItemDelete: ((Consultation, Int) -> Unit)? = null
    var onItemEdit: ((Consultation, String, Int) -> Unit)? = null
    var onItemDetailsClick: ((Consultation) -> Unit)? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clientName: TextView = itemView.findViewById(R.id.tvClientName)
        val consultationDateTime: TextView = itemView.findViewById(R.id.tvConsultationTime)
        val notes: TextView = itemView.findViewById(R.id.tvNotes)
        val problem: TextView = itemView.findViewById(R.id.tvProblem) // Added problem TextView
        val editButton: Button = itemView.findViewById(R.id.btnEdit)
        val detailsButton: Button = itemView.findViewById(R.id.btnDetails)

        // Attachment-related views, only initialized when needed
        val attachmentsSection: LinearLayout? by lazy { itemView.findViewById<LinearLayout>(R.id.attachmentsSection) }
        val noAttachmentsText: TextView? by lazy { itemView.findViewById<TextView>(R.id.tvNoAttachments) }
        val attachmentsContainer: LinearLayout? by lazy { itemView.findViewById<LinearLayout>(R.id.attachmentsContainer) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_consultation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val consultation = consultationList[position]
        holder.clientName.text = "Client: ${consultation.clientName}"
        holder.consultationDateTime.text = "Date: ${consultation.consultationDate} • Time: ${consultation.consultationTime}"
        holder.notes.text = "Notes: ${consultation.notes}"
        holder.problem.text = consultation.problem // Set problem text

        // Debug log to check if attachments exist
        Log.d("ConsultationAdapter", "Attachments for ${consultation.clientName}: ${consultation.attachments?.size ?: 0}")

        // Handle attachments if the view exists
        holder.itemView.findViewById<LinearLayout>(R.id.attachmentsSection)?.let { section ->
            holder.itemView.findViewById<TextView>(R.id.tvNoAttachments)?.let { noAttachmentsText ->
                holder.itemView.findViewById<LinearLayout>(R.id.attachmentsContainer)?.let { container ->
                    if (!consultation.attachments.isNullOrEmpty()) {
                        Log.d("ConsultationAdapter", "Showing attachments: ${consultation.attachments}")
                        section.visibility = View.VISIBLE
                        noAttachmentsText.visibility = View.GONE
                        container.visibility = View.VISIBLE
                        container.removeAllViews()

                        for (attachment in consultation.attachments!!) {
                            val attachmentView = TextView(holder.itemView.context)
                            attachmentView.text = getFileNameFromUrl(attachment)
                            attachmentView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
                            attachmentView.setPadding(0, 8, 0, 8)
                            attachmentView.setOnClickListener {
                                openAttachment(holder.itemView.context, attachment)
                            }
                            container.addView(attachmentView)
                        }
                    } else {
                        Log.d("ConsultationAdapter", "No attachments to show")
                        section.visibility = View.VISIBLE
                        noAttachmentsText.visibility = View.VISIBLE
                        container.visibility = View.GONE
                    }
                }
            }
        }

        holder.editButton.setOnClickListener {
            showEditDialog(holder.itemView.context, consultation, position)
        }

        holder.detailsButton.setOnClickListener {
            onItemDetailsClick?.invoke(consultation)
        }
    }

    private fun showEditDialog(context: android.content.Context, consultation: Consultation, position: Int) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_notes, null)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.etNotes)

        notesInput.setText(consultation.notes)

        AlertDialog.Builder(context)
            .setTitle("Edit Consultation Notes")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newNotes = notesInput.text.toString().trim()
                if (newNotes != consultation.notes) {
                    onItemEdit?.invoke(consultation, newNotes, position)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Helper method to get file name from URL
    private fun getFileNameFromUrl(url: String): String {
        return try {
            url.substring(url.lastIndexOf('/') + 1)
        } catch (e: Exception) {
            "Attachment"
        }
    }

    // Helper method to handle attachment clicks
    private fun openAttachment(context: Context, url: String) {
        try {
            val extension = url.substringAfterLast('.', "").lowercase()
            if (extension == "pdf" || extension == "doc" || extension == "docx") {
                downloadFile(context, url)
            } else {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(url), getMimeType(url))
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper method to get MIME type
    private fun getMimeType(url: String): String {
        val extension = url.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    // Helper method to download files
    private fun downloadFile(context: Context, url: String) {
        try {
            val fileName = getFileNameFromUrl(url)
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(fileName)
            request.setDescription("Downloading $fileName")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DownloadError", "Error downloading file: ${e.message}")
            Toast.makeText(context, "Failed to download file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int {
        return consultationList.size
    }

    fun updateConsultations(newList: ArrayList<Consultation>) {
        consultationList.clear()
        consultationList.addAll(newList)
        notifyDataSetChanged()
    }

    fun deleteItem(position: Int) {
        val deletedItem = consultationList[position]
        consultationList.removeAt(position)
        notifyItemRemoved(position)
        onItemDelete?.invoke(deletedItem, position)
    }

    fun updateItem(position: Int, newNotes: String) {
        consultationList[position] = consultationList[position].copy(notes = newNotes)
        notifyItemChanged(position)
    }
}