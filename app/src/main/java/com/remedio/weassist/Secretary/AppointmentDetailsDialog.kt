package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.R

class AppointmentDetailsDialog : DialogFragment() {

    private lateinit var appointment: Appointment
    private var isSecretaryView: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appointment = it.getParcelable("appointment") ?: Appointment()
            isSecretaryView = it.getBoolean("isSecretaryView", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_appointment_details_dialog, container, false)

        val nameTextView: TextView = view.findViewById(R.id.appointment_full_name)
        val dateTextView: TextView = view.findViewById(R.id.appointment_date)
        val timeTextView: TextView = view.findViewById(R.id.appointment_time)
        val problemTextView: TextView = view.findViewById(R.id.appointment_problem)
        val acceptButton: Button = view.findViewById(R.id.btn_accept)
        val declineButton: Button = view.findViewById(R.id.btn_decline)

        nameTextView.text = "Client: ${appointment.fullName ?: "N/A"}"
        dateTextView.text = "Date: ${appointment.date ?: "N/A"}"
        timeTextView.text = "Time: ${appointment.time ?: "N/A"}"
        problemTextView.text = "Problem: ${appointment.problem ?: "N/A"}"

        if (isSecretaryView) {
            acceptButton.visibility = View.VISIBLE
            declineButton.visibility = View.VISIBLE
        } else {
            acceptButton.visibility = View.GONE
            declineButton.visibility = View.GONE
        }

        acceptButton.setOnClickListener {
            getSecretaryIdAndAcceptAppointment(appointment.appointmentId)
            dismiss()
        }

        declineButton.setOnClickListener {
            deleteAppointment(appointment.appointmentId)
            dismiss()
        }

        return view
    }

    private fun getSecretaryIdAndAcceptAppointment(appointmentId: String) {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("secretaries")
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        databaseRef.orderByChild("email").equalTo(currentUserEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (data in snapshot.children) {
                        val secretaryId = data.key // Get the dynamic secretary ID
                        if (secretaryId != null) {
                            acceptAppointment(appointmentId, secretaryId)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error fetching secretary ID: ${error.message}")
                }
            })
    }

    private fun acceptAppointment(appointmentId: String, secretaryId: String) {
        val database = FirebaseDatabase.getInstance().reference

        // Update the secretary ID in the appointment object
        appointment.secretaryId = secretaryId

        val acceptedAppointmentsRef = database.child("accepted_appointment").child(appointmentId)
        acceptedAppointmentsRef.setValue(appointment)
            .addOnSuccessListener { Log.d("Appointment", "Appointment accepted and saved under accepted_appointment") }
            .addOnFailureListener { Log.e("Appointment", "Failed to accept appointment") }

        val clientAppointmentsRef = database.child("users").child(appointment.clientId).child("appointments").child(appointmentId)
        clientAppointmentsRef.setValue(appointment)
            .addOnSuccessListener { Log.d("Appointment", "Added to client appointments") }
            .addOnFailureListener { Log.e("Appointment", "Failed to add to client appointments") }

        val lawyerAppointmentsRef = database.child("lawyers").child(appointment.lawyerId).child("appointments").child(appointmentId)
        lawyerAppointmentsRef.setValue(appointment)
            .addOnSuccessListener { Log.d("Appointment", "Added to lawyer appointments") }
            .addOnFailureListener { Log.e("Appointment", "Failed to add to lawyer appointments") }

        val secretaryAppointmentsRef = database.child("secretaries").child(secretaryId).child("appointments").child(appointmentId)
        secretaryAppointmentsRef.setValue(appointment)
            .addOnSuccessListener { Log.d("Appointment", "Added to secretary appointments") }
            .addOnFailureListener { Log.e("Appointment", "Failed to add to secretary appointments") }

        val appointmentsRef = database.child("appointments").child(appointmentId)
        appointmentsRef.removeValue()
            .addOnSuccessListener { Log.d("Appointment", "Removed from original appointments") }
            .addOnFailureListener { Log.e("Appointment", "Failed to remove from original list") }
    }

    private fun deleteAppointment(appointmentId: String) {
        val appointmentRef = FirebaseDatabase.getInstance().reference.child("appointments").child(appointmentId)
        appointmentRef.removeValue()
            .addOnSuccessListener { Log.d("Appointment", "Appointment removed from the database") }
            .addOnFailureListener { Log.e("Appointment", "Failed to remove appointment") }
    }

    companion object {
        fun newInstance(appointment: Appointment, isSecretaryView: Boolean): AppointmentDetailsDialog {
            val fragment = AppointmentDetailsDialog()
            val args = Bundle()
            args.putParcelable("appointment", appointment)
            args.putBoolean("isSecretaryView", isSecretaryView)
            fragment.arguments = args
            return fragment
        }
    }
}
