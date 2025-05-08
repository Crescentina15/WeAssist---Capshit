package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.SetAppointmentActivity

class FileAttachmentAdapter(
    private val files: List<SetAppointmentActivity.FileAttachment>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<FileAttachmentAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileSize: TextView = itemView.findViewById(R.id.file_size)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attached_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.fileName
        holder.fileSize.text = file.fileSize
        holder.deleteButton.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount() = files.size
}