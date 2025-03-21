package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R
import com.squareup.picasso.Picasso

class AppointmentAdapter(
    private var appointments: List<Appointment>,
    private val isClickable: Boolean,
    private val isClientView: Boolean = false,
    private val onItemClickListener: ((Appointment) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SECRETARY = 0
        private const val VIEW_TYPE_CLIENT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isClientView) VIEW_TYPE_CLIENT else VIEW_TYPE_SECRETARY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CLIENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_appointment_client, parent, false)
                ClientAppointmentViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_appointment, parent, false)
                SecretaryAppointmentViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val appointment = appointments[position]

        when (holder) {
            is SecretaryAppointmentViewHolder -> bindSecretaryViewHolder(holder, appointment)
            is ClientAppointmentViewHolder -> bindClientViewHolder(holder, appointment)
        }

        if (isClickable) {
            holder.itemView.setOnClickListener {
                onItemClickListener?.invoke(appointment)
            }
        } else {
            holder.itemView.isClickable = false
            holder.itemView.isFocusable = false
            holder.itemView.alpha = 0.6f
        }
    }

    private fun bindSecretaryViewHolder(holder: SecretaryAppointmentViewHolder, appointment: Appointment) {
        holder.appointmentTitle.text = "Appointment with ${appointment.fullName}"
        holder.appointmentDate.text = appointment.date

        // Check if the time is booked
        if (appointment.status?.equals("Booked", ignoreCase = true) == true) {
            holder.appointmentTime.text = "Time already booked, please select another schedule"
        } else {
            holder.appointmentTime.text = appointment.time
        }

        holder.lawyerName.text = "For Atty. ${appointment.lawyerName}"
        holder.appointmentStatus.text = appointment.status ?: "Pending"

        // Load the lawyer's profile image
        if (!appointment.lawyerProfileImage.isNullOrEmpty()) {
            Picasso.get().load(appointment.lawyerProfileImage).into(holder.lawyerProfileImage)
        } else {
            holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24) // Default image
        }
    }

    private fun bindClientViewHolder(holder: ClientAppointmentViewHolder, appointment: Appointment) {
        holder.appointmentTitle?.text = "Atty. ${appointment.lawyerName ?: "Unknown"} accepted your appointment"
        holder.appointmentDate?.text = appointment.date

        // Check if the time is booked
        if (appointment.status?.equals("Booked", ignoreCase = true) == true) {
            holder.appointmentTime?.text = "Time already booked, please select another schedule"
        } else {
            holder.appointmentTime?.text = appointment.time
        }

        holder.lawyerName?.text = "For Atty. : ${appointment.lawyerName ?: "Unknown"}"
        holder.appointmentStatus?.text = appointment.status ?: "Pending"

        holder.lawyerProfileImage?.let { imageView ->
            if (!appointment.lawyerProfileImage.isNullOrEmpty()) {
                Picasso.get().load(appointment.lawyerProfileImage).into(imageView)
            } else {
                imageView.setImageResource(R.drawable.account_circle_24)
            }
        }
    }

    override fun getItemCount(): Int = appointments.size

    class SecretaryAppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
        val appointmentTitle: TextView = itemView.findViewById(R.id.appointment_title)
        val lawyerName: TextView = itemView.findViewById(R.id.lawyer_name)
        val appointmentDate: TextView = itemView.findViewById(R.id.appointment_date)
        val appointmentTime: TextView = itemView.findViewById(R.id.appointment_time)
        val appointmentStatus: TextView = itemView.findViewById(R.id.appointment_status)
    }

    fun updateAppointments(newAppointments: List<Appointment>) {
        this.appointments = newAppointments
        notifyDataSetChanged()
    }

    class ClientAppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView? = itemView.findViewById(R.id.lawyer_profile_image)
        val appointmentTitle: TextView? = itemView.findViewById(R.id.appointment_title)
        val appointmentDate: TextView? = itemView.findViewById(R.id.appointment_date)
        val appointmentTime: TextView? = itemView.findViewById(R.id.appointment_time)
        val lawyerName: TextView? = itemView.findViewById(R.id.lawyer_name)
        val appointmentStatus: TextView? = itemView.findViewById(R.id.appointment_status)
    }
}