package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.AppointmentDetailsDialog
import com.squareup.picasso.Picasso

class AppointmentAdapter(
    private val appointments: List<Appointment>,
    private val isClickable: Boolean, // New parameter to control clickability
    private val isClientView: Boolean = false, // New parameter to determine the layout
    private val onItemClickListener: ((Appointment) -> Unit)? = null // New parameter for click listener
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        // Determine which layout to use based on isClientView
        val layoutRes = if (isClientView) R.layout.item_appointment_client else R.layout.item_appointment
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val appointment = appointments[position]
        holder.appointmentTitle.text = "Appointment with ${appointment.fullName}"
        holder.appointmentDate.text = appointment.date
        holder.appointmentTime.text = appointment.time

        if (!appointment.lawyerProfileImage.isNullOrEmpty()) {
            Picasso.get().load(appointment.lawyerProfileImage).into(holder.lawyerProfileImage)
        } else {
            holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)
        }

        if (isClickable) {
            // Enable click and open the appointment details dialog
            holder.itemView.setOnClickListener {
                onItemClickListener?.invoke(appointment)
            }
        } else {
            // Disable clickability
            holder.itemView.isClickable = false
            holder.itemView.isFocusable = false
            holder.itemView.alpha = 0.6f // Optional: Reduce opacity to show it's non-interactable
        }

        // Display the appointment status (e.g., "Accepted")
        holder.appointmentStatus.text = appointment.status ?: "Pending"
    }

    override fun getItemCount(): Int = appointments.size

    class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
        val appointmentTitle: TextView = itemView.findViewById(R.id.appointment_title)
        val appointmentDate: TextView = itemView.findViewById(R.id.appointment_date)
        val appointmentTime: TextView = itemView.findViewById(R.id.appointment_time)
        val appointmentStatus: TextView = itemView.findViewById(R.id.appointment_status) // Add this TextView in your XML
    }
}