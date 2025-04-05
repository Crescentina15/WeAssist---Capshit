package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.FirebaseDatabase
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

        // Load lawyer profile image using Glide if URL is available
        if (!appointment.lawyerProfileImage.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(appointment.lawyerProfileImage)
                .placeholder(R.drawable.account_circle_24)
                .error(R.drawable.account_circle_24)
                .into(holder.lawyerProfileImage)
        } else {
            // Set default image if no URL is available
            holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)
        }

        // Get the session state for this appointment, default to false (not started)
        val isSessionActive = sessionStates[appointment.appointmentId] ?: false
        holder.sessionButton.text = if (isSessionActive) "End Session" else "Start Session"

        holder.sessionButton.setOnClickListener {
            val newState = !(sessionStates[appointment.appointmentId] ?: false)
            sessionStates[appointment.appointmentId] = newState
            holder.sessionButton.text = if (newState) "End Session" else "Start Session"
            onSessionToggle(appointment, newState)

            // Update in Firebase
            val sessionRef = FirebaseDatabase.getInstance().reference
                .child("lawyers").child(appointment.lawyerId).child("active_sessions")
                .child(appointment.appointmentId)

            if (newState) {
                sessionRef.setValue(true)
            } else {
                sessionRef.removeValue()
            }
        }
    }

    override fun getItemCount(): Int = appointmentList.size
}