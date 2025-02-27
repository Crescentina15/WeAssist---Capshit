package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class SecretaryAppointmentAdapter(
    private val appointmentList: List<Appointment>,
    private val onSessionToggle: (Appointment, Boolean) -> Unit // Callback to handle session toggle
) : RecyclerView.Adapter<SecretaryAppointmentAdapter.SecretaryAppointmentViewHolder>() {

    // Store session states for each appointment
    private val sessionStates = mutableMapOf<String, Boolean>()

    class SecretaryAppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
        val taskTitle: TextView = itemView.findViewById(R.id.task_title)
        val taskDate: TextView = itemView.findViewById(R.id.task_date)
        val taskTime: TextView = itemView.findViewById(R.id.task_time)
        val sessionButton: Button = itemView.findViewById(R.id.btn_session)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretaryAppointmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_item_card, parent, false) // Ensure correct layout name
        return SecretaryAppointmentViewHolder(view)
    }


    override fun onBindViewHolder(holder: SecretaryAppointmentViewHolder, position: Int) {
        val appointment = appointmentList[position]

        holder.taskTitle.text = "Appointment with Atty. ${appointment.fullName}"
        holder.taskDate.text = appointment.date
        holder.taskTime.text = appointment.time

        // Set default lawyer profile image
        holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)

        // Get the session state for this appointment, default to false (not started)
        val isSessionActive = sessionStates[appointment.appointmentId] ?: false
        holder.sessionButton.text = if (isSessionActive) "End Session" else "Start Session"

        // Handle session button click
        holder.sessionButton.setOnClickListener {
            val newState = !(sessionStates[appointment.appointmentId] ?: false) // Toggle state
            sessionStates[appointment.appointmentId] = newState // Save new state

            holder.sessionButton.text = if (newState) "End Session" else "Start Session"
            onSessionToggle(appointment, newState) // Callback to handle session start/end logic
        }
    }

    override fun getItemCount(): Int = appointmentList.size
}
