package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.remedio.weassist.R

class ConsultationAdapter(private val consultationList: ArrayList<Consultation>) :
    RecyclerView.Adapter<ConsultationAdapter.ViewHolder>() {

    var onItemDelete: ((Consultation, Int) -> Unit)? = null
    var onItemEdit: ((Consultation, String, Int) -> Unit)? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clientName: TextView = itemView.findViewById(R.id.tvClientName)
        val consultationDateTime: TextView = itemView.findViewById(R.id.tvConsultationTime)
        val notes: TextView = itemView.findViewById(R.id.tvNotes)
        val editButton: Button = itemView.findViewById(R.id.btnEdit)
        val detailsButton: Button = itemView.findViewById(R.id.btnDetails)
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

        holder.editButton.setOnClickListener {
            showEditDialog(holder.itemView.context, consultation, position)
        }

        // Keep your existing details button functionality
        holder.detailsButton.setOnClickListener {
            // Add your details button logic here
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