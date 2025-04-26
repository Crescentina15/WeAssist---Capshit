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
    private val appointmentList: MutableList<Appointment>,
    private val onSessionStart: (Appointment) -> Unit
) : RecyclerView.Adapter<SecretaryAppointmentAdapter.SecretaryAppointmentViewHolder>() {

    private val sessionStates = mutableMapOf<String, Boolean>()
    private val sessionListeners = mutableMapOf<String, ChildEventListener>()

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

        // Check session state from Firebase when binding
        checkSessionState(appointment)

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

    private fun checkSessionState(appointment: Appointment) {
        // Create a single-time listener to get the current state
        FirebaseDatabase.getInstance().reference
            .child("lawyers").child(appointment.lawyerId).child("active_sessions")
            .child(appointment.appointmentId)
            .get()
            .addOnSuccessListener { snapshot ->
                val isActive = snapshot.exists() && (snapshot.getValue(Boolean::class.java) == true)
                if (sessionStates[appointment.appointmentId] != isActive) {
                    sessionStates[appointment.appointmentId] = isActive
                    notifyDataSetChanged()
                }
            }
    }

    fun updateSessionState(appointmentId: String, isActive: Boolean) {
        sessionStates[appointmentId] = isActive
        notifyDataSetChanged()
    }

    fun startListeningForSessions() {
        stopListeningForSessions()

        // Add listeners for each appointment
        for (appointment in appointmentList) {
            val listener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    sessionStates[appointment.appointmentId] = true
                    notifyDataSetChanged()
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    sessionStates[appointment.appointmentId] = snapshot.getValue(Boolean::class.java) ?: false
                    notifyDataSetChanged()
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    sessionStates[appointment.appointmentId] = false
                    notifyDataSetChanged()
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAdapter", "Session listener cancelled", error.toException())
                }
            }

            // Use value event listener instead of child event listener for the specific appointment
            FirebaseDatabase.getInstance().reference
                .child("lawyers").child(appointment.lawyerId).child("active_sessions")
                .addChildEventListener(listener)

            sessionListeners[appointment.appointmentId] = listener
        }
    }

    fun stopListeningForSessions() {
        // Remove all session listeners
        for (appointment in appointmentList) {
            sessionListeners[appointment.appointmentId]?.let { listener ->
                FirebaseDatabase.getInstance().reference
                    .child("lawyers").child(appointment.lawyerId).child("active_sessions")
                    .removeEventListener(listener)
            }
        }
        sessionListeners.clear()
    }

    // Appointments listener for real-time updates
    private var appointmentListener: ChildEventListener? = null

    fun startListeningForAppointments() {
        stopListeningForAppointments()

        appointmentListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Handled by the fragment
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handled by the fragment
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val appointmentId = snapshot.key ?: return
                val position = appointmentList.indexOfFirst { it.appointmentId == appointmentId }
                if (position != -1) {
                    // Also remove any associated session listeners
                    sessionListeners[appointmentId]?.let { listener ->
                        appointmentList[position].lawyerId.let { lawyerId ->
                            FirebaseDatabase.getInstance().reference
                                .child("lawyers").child(lawyerId).child("active_sessions")
                                .removeEventListener(listener)
                        }
                    }
                    sessionListeners.remove(appointmentId)
                    sessionStates.remove(appointmentId)

                    appointmentList.removeAt(position)
                    notifyItemRemoved(position)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAdapter", "Appointment listener cancelled", error.toException())
            }
        }

        // Listen to the accepted_appointment node
        FirebaseDatabase.getInstance().reference
            .child("accepted_appointment")
            .addChildEventListener(appointmentListener!!)
    }

    fun stopListeningForAppointments() {
        appointmentListener?.let {
            FirebaseDatabase.getInstance().reference
                .child("accepted_appointment")
                .removeEventListener(it)
        }
        appointmentListener = null
    }

    override fun getItemCount(): Int = appointmentList.size
}