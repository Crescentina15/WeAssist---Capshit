package com.remedio.weassist

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.Secretary.Appointment

class AppointmentDetailsDialog : DialogFragment() {

    private lateinit var appointment: Appointment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appointment = it.getParcelable("appointment") ?: Appointment()
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

        acceptButton.setOnClickListener {
            updateAppointmentStatus(appointment.appointmentId, "Accepted")
            dismiss()
        }

        declineButton.setOnClickListener {
            updateAppointmentStatus(appointment.appointmentId, "Declined")
            dismiss()
        }

        return view
    }

    private fun updateAppointmentStatus(appointmentId: String, status: String) {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("appointments").child(appointmentId)
        databaseRef.child("status").setValue(status)
            .addOnSuccessListener { Log.d("Appointment", "Updated to $status") }
            .addOnFailureListener { Log.e("Appointment", "Failed to update status") }
    }

    companion object {
        fun newInstance(appointment: Appointment): AppointmentDetailsDialog {
            val fragment = AppointmentDetailsDialog()
            val args = Bundle()
            args.putParcelable("appointment", appointment)
            fragment.arguments = args
            return fragment
        }
    }
}
