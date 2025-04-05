package com.remedio.weassist.Models

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R

class SecretaryAppointmentAdapter(
    private val appointmentList: List<Appointment>,
    private val onSessionStart: (Appointment) -> Unit
) : RecyclerView.Adapter<SecretaryAppointmentAdapter.SecretaryAppointmentViewHolder>() {

    private val sessionStates = mutableMapOf<String, Boolean>()
    private var sessionListener: ChildEventListener? = null

    class SecretaryAppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
        val taskTitle: TextView = itemView.findViewById(R.id.task_title)
        val taskDate: TextView = itemView.findViewById(R.id.task_date)
        val taskTime: TextView = itemView.findViewById(R.id.task_time)
        val sessionButton: Button = itemView.findViewById(R.id.btn_session)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretaryAppointmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_item_card, parent, false)
        return SecretaryAppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: SecretaryAppointmentViewHolder, position: Int) {
        val appointment = appointmentList[position]
        val isSessionActive = sessionStates[appointment.appointmentId] ?: false

        // Set appointment details
        holder.taskTitle.text = "Appointment with Atty. ${appointment.fullName}"
        holder.taskDate.text = appointment.date
        holder.taskTime.text = appointment.time

        // Load lawyer profile image
        if (!appointment.lawyerProfileImage.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(appointment.lawyerProfileImage)
                .placeholder(R.drawable.account_circle_24)
                .error(R.drawable.account_circle_24)
                .into(holder.lawyerProfileImage)
        } else {
            holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)
        }

        // Configure session button based on state
        if (isSessionActive) {
            holder.sessionButton.text = "Session Started"
            holder.sessionButton.isEnabled = false
            holder.sessionButton.setBackgroundColor(Color.GRAY)
            holder.sessionButton.alpha = 0.7f
        } else {
            holder.sessionButton.text = "Start Session"
            holder.sessionButton.isEnabled = true
            holder.sessionButton.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.green)
            )
            holder.sessionButton.alpha = 1f
        }

        // Set click listener only for inactive sessions
        holder.sessionButton.setOnClickListener {
            if (!isSessionActive) {
                // Update local state immediately for better UX
                sessionStates[appointment.appointmentId] = true
                notifyItemChanged(position)

                // Notify fragment and update Firebase
                onSessionStart(appointment)
            }
        }
    }

    fun updateSessionState(appointmentId: String, isActive: Boolean) {
        sessionStates[appointmentId] = isActive
        notifyDataSetChanged()
    }


    fun startListeningForSessions() {
        stopListeningForSessions()

        sessionListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                updateSessionState(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                updateSessionState(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val appointmentId = snapshot.key ?: return
                sessionStates[appointmentId] = false
                notifyDataSetChanged()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAdapter", "Session listener cancelled", error.toException())
            }
        }

        appointmentList.forEach { appointment ->
            FirebaseDatabase.getInstance().reference
                .child("lawyers").child(appointment.lawyerId).child("active_sessions")
                .child(appointment.appointmentId)
                .addChildEventListener(sessionListener!!)
        }
    }

    private fun updateSessionState(snapshot: DataSnapshot) {
        val appointmentId = snapshot.key ?: return
        val isActive = snapshot.getValue(Boolean::class.java) ?: false
        sessionStates[appointmentId] = isActive
        notifyDataSetChanged()
    }

    fun stopListeningForSessions() {
        sessionListener?.let { listener ->
            appointmentList.forEach { appointment ->
                FirebaseDatabase.getInstance().reference
                    .child("lawyers").child(appointment.lawyerId).child("active_sessions")
                    .child(appointment.appointmentId)
                    .removeEventListener(listener)
            }
        }
        sessionListener = null
    }

    override fun onViewDetachedFromWindow(holder: SecretaryAppointmentViewHolder) {
        super.onViewDetachedFromWindow(holder)
        stopListeningForSessions()
    }

    override fun onViewAttachedToWindow(holder: SecretaryAppointmentViewHolder) {
        super.onViewAttachedToWindow(holder)
        startListeningForSessions()
    }

    override fun getItemCount(): Int = appointmentList.size


}