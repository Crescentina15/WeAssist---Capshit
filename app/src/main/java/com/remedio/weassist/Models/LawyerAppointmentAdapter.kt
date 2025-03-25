package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class LawyerAppointmentAdapter(
    private var appointments: List<Appointment>,
    private val onItemClick: (Appointment) -> Unit
) : RecyclerView.Adapter<LawyerAppointmentAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clientNameTextView: TextView = itemView.findViewById(R.id.client_name_text_view)
        val consultationDateTextView: TextView = itemView.findViewById(R.id.consultation_date_text_view)
        val consultationTimeTextView: TextView = itemView.findViewById(R.id.consultation_time_text_view)
        val problemTextView: TextView = itemView.findViewById(R.id.problem_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lawyer_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appointment = appointments[position]

        holder.clientNameTextView.text = appointment.fullName
        holder.consultationDateTextView.text = appointment.date
        holder.consultationTimeTextView.text = appointment.time
        holder.problemTextView.text = appointment.problem

        holder.itemView.setOnClickListener {
            onItemClick(appointment)
        }
    }

    override fun getItemCount(): Int = appointments.size

    fun updateAppointments(newAppointments: List<Appointment>) {
        appointments = newAppointments
        notifyDataSetChanged()
    }
}