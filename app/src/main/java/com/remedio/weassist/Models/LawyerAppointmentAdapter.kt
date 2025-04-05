package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class LawyerAppointmentAdapter(
    private var appointments: List<Appointment>,
    private val onItemClick: (Appointment) -> Unit,
    private val onEndSessionClick: (Appointment) -> Unit
) : RecyclerView.Adapter<LawyerAppointmentAdapter.ViewHolder>() {

    private val sessionStates = mutableMapOf<String, Boolean>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clientNameTextView: TextView = itemView.findViewById(R.id.client_name_text_view)
        val consultationDateTextView: TextView = itemView.findViewById(R.id.consultation_date_text_view)
        val consultationTimeTextView: TextView = itemView.findViewById(R.id.consultation_time_text_view)
        val problemTextView: TextView = itemView.findViewById(R.id.problem_text_view)
        val endSessionButton: Button = itemView.findViewById(R.id.end_session_button)
        val sessionStatusText: TextView = itemView.findViewById(R.id.session_status_text) // Add this TextView to your layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lawyer_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appointment = appointments[position]
        val isSessionActive = sessionStates[appointment.appointmentId] ?: false

        holder.clientNameTextView.text = appointment.fullName
        holder.consultationDateTextView.text = appointment.date
        holder.consultationTimeTextView.text = appointment.time
        holder.problemTextView.text = appointment.problem

        if (isSessionActive) {
            holder.endSessionButton.visibility = View.VISIBLE
            holder.sessionStatusText.visibility = View.GONE
        } else {
            holder.endSessionButton.visibility = View.GONE
            holder.sessionStatusText.visibility = View.VISIBLE
            holder.sessionStatusText.text = "No active session"
        }

        holder.itemView.setOnClickListener {
            onItemClick(appointment)
        }

        holder.endSessionButton.setOnClickListener {
            onEndSessionClick(appointment)
            sessionStates[appointment.appointmentId] = false
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = appointments.size

    fun updateAppointments(newAppointments: List<Appointment>) {
        appointments = newAppointments
        notifyDataSetChanged()
    }

    fun setSessionActive(appointmentId: String, isActive: Boolean) {
        sessionStates[appointmentId] = isActive
        notifyDataSetChanged()
    }
    fun removeAppointment(appointmentId: String) {
        // Create a new list without the removed appointment
        val newList = appointments.filterNot { it.appointmentId == appointmentId }
        updateAppointments(newList)
    }
    fun removeAppointmentById(appointmentId: String) {
        // Create a new list without the removed appointment
        val newList = appointments.filterNot { it.appointmentId == appointmentId }
        updateAppointments(newList)
    }

}