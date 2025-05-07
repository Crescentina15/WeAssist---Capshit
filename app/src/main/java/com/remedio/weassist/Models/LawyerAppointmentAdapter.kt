package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
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
        val sessionStatusText: TextView = itemView.findViewById(R.id.session_status_text)
        val appointmentBackground: View = itemView.findViewById(R.id.appointment_background) // For graying out
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
            holder.appointmentBackground.isEnabled = true
            holder.appointmentBackground.alpha = 1.0f // Make fully visible
        } else {
            holder.endSessionButton.visibility = View.GONE
            holder.sessionStatusText.visibility = View.VISIBLE
            holder.sessionStatusText.text = "No active session"
            holder.appointmentBackground.isEnabled = false
            holder.appointmentBackground.alpha = 0.5f // Gray out
        }

        holder.itemView.setOnClickListener {
            if (isSessionActive) {
                onItemClick(appointment)
            }
        }

        holder.endSessionButton.setOnClickListener {
            // Update appointment status to "Complete" in Firebase
            val database = FirebaseDatabase.getInstance().reference
            database.child("appointments").child(appointment.appointmentId)
                .child("status").setValue("Complete")
                .addOnSuccessListener {
                    onEndSessionClick(appointment)
                    sessionStates[appointment.appointmentId] = false
                    notifyItemChanged(position)
                }
                .addOnFailureListener {
                    // Handle failure if needed
                }
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
        val newList = appointments.filterNot { it.appointmentId == appointmentId }
        updateAppointments(newList)
    }
}