package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class ConsultationAdapter(private val consultationList: ArrayList<Consultation>) :
    RecyclerView.Adapter<ConsultationAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clientName: TextView = itemView.findViewById(R.id.tvClientName)
        val consultationDateTime: TextView = itemView.findViewById(R.id.tvConsultationTime) // Rename this in layout
        val notes: TextView = itemView.findViewById(R.id.tvNotes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_consultation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val consultation = consultationList[position]
        holder.clientName.text = "Client: ${consultation.clientName}"
        // Combine date and time in the display
        holder.consultationDateTime.text = "Date: ${consultation.consultationDate} • Time: ${consultation.consultationTime}"
        holder.notes.text = "Notes: ${consultation.notes}"
    }

    override fun getItemCount(): Int {
        return consultationList.size
    }

    // Add this function to update the list
    fun updateConsultations(newList: ArrayList<Consultation>) {
        consultationList.clear()
        consultationList.addAll(newList)
        notifyDataSetChanged()
    }
}