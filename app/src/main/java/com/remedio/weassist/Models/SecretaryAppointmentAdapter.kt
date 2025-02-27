package com.remedio.weassist.Models



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.R

class SecretaryAppointmentAdapter(
    private val appointmentList: List<Appointment>,
    private val onEndSessionClick: (Appointment) -> Unit
) : RecyclerView.Adapter<SecretaryAppointmentAdapter.SecretaryAppointmentViewHolder>() {

    class SecretaryAppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
        val taskTitle: TextView = itemView.findViewById(R.id.task_title)
        val taskDate: TextView = itemView.findViewById(R.id.task_date)
        val taskTime: TextView = itemView.findViewById(R.id.task_time)
        val endSessionButton: Button = itemView.findViewById(R.id.btn_end_session)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretaryAppointmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_item_card, parent, false)
        return SecretaryAppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: SecretaryAppointmentViewHolder, position: Int) {
        val appointment = appointmentList[position]

        holder.taskTitle.text = "Appointment with Atty. ${appointment.fullName}"
        holder.taskDate.text = appointment.date
        holder.taskTime.text = appointment.time

        // Set default lawyer profile image
        holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)

        // Handle end session button click
        holder.endSessionButton.setOnClickListener {
            onEndSessionClick(appointment)
        }
    }

    override fun getItemCount(): Int = appointmentList.size
}
