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
    private val isClickable: Boolean // New parameter to control clickability
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appointment, parent, false)
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
                val dialog = AppointmentDetailsDialog.newInstance(appointment)
                dialog.show(
                    (holder.itemView.context as androidx.fragment.app.FragmentActivity).supportFragmentManager,
                    "AppointmentDetailsDialog"
                )
            }
        } else {
            // Disable clickability
            holder.itemView.isClickable = false
            holder.itemView.isFocusable = false
            holder.itemView.alpha = 0.6f // Optional: Reduce opacity to show it's non-interactable
        }
    }

    override fun getItemCount(): Int = appointments.size

    class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
        val appointmentTitle: TextView = itemView.findViewById(R.id.appointment_title)
        val appointmentDate: TextView = itemView.findViewById(R.id.appointment_date)
        val appointmentTime: TextView = itemView.findViewById(R.id.appointment_time)
    }
}
