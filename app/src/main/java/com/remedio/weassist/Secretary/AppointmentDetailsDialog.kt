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
                        val secretaryName = data.child("name").getValue(String::class.java) ?: "Secretary"
                        if (secretaryId != null) {
                            acceptAppointment(appointmentId, secretaryId, secretaryName)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error fetching secretary ID: ${error.message}")
                }
            })
    }

    private fun acceptAppointment(appointmentId: String, secretaryId: String, secretaryName: String) {
        val database = FirebaseDatabase.getInstance().reference

        // Retrieve the appointment from Firebase first
        val appointmentRef = database.child("appointments").child(appointmentId)
        appointmentRef.get().addOnSuccessListener { snapshot ->
            val appointment = snapshot.getValue(Appointment::class.java)
            if (appointment != null) {
                // Update appointment status to "Accepted"
                val updatedAppointment = appointment.copy(
                    secretaryId = secretaryId,
                    status = "Accepted" // Change status
                )

                // Save updated appointment in the accepted_appointment collection
                val acceptedAppointmentsRef = database.child("accepted_appointment").child(appointmentId)
                acceptedAppointmentsRef.setValue(updatedAppointment)

                // Update in User, Lawyer, and Secretary collections
                val clientAppointmentsRef = database.child("users").child(appointment.clientId).child("appointments").child(appointmentId)
                clientAppointmentsRef.setValue(updatedAppointment)

                val lawyerAppointmentsRef = database.child("lawyers").child(appointment.lawyerId).child("appointments").child(appointmentId)
                lawyerAppointmentsRef.setValue(updatedAppointment)

                val secretaryAppointmentsRef = database.child("secretaries").child(secretaryId).child("appointments").child(appointmentId)
                secretaryAppointmentsRef.setValue(updatedAppointment)

                // Send notification to client
                sendNotificationToClient(appointment.clientId, secretaryId, secretaryName, appointment)

                // Send notification to lawyer
                sendNotificationToLawyer(appointment.lawyerId, secretaryId, secretaryName, appointment)

                // Remove from the main appointments list
                appointmentRef.removeValue()
            }
        }.addOnFailureListener {
            Log.e("Appointment", "Failed to fetch appointment data")
        }
    }

    private fun sendNotificationToLawyer(lawyerId: String, secretaryId: String, secretaryName: String, appointment: Appointment) {
        val database = FirebaseDatabase.getInstance().reference
        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to secretaryId,
            "senderName" to secretaryName,
            "message" to "You have an appointment with ${appointment.fullName} on ${appointment.date} at ${appointment.time}.",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "appointment_accepted",
            "isRead" to false,
            "appointmentId" to appointment.appointmentId
        )

        database.child("notifications").child(lawyerId).child(notificationId)
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d("Notification", "Notification sent to lawyer successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to send notification to lawyer: ${e.message}")
            }
    }

    private fun sendNotificationToClient(clientId: String, secretaryId: String, secretaryName: String, appointment: Appointment) {
        val database = FirebaseDatabase.getInstance().reference
        val notificationId = database.child("notifications").child(clientId).push().key ?: return

        val notificationData = mapOf(
            "senderId" to secretaryId,
            "message" to "Your appointment on ${appointment.date} at ${appointment.time} has been accepted",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "appointment_accepted",
            "isRead" to false,
            "appointmentId" to appointment.appointmentId
        )

        database.child("notifications").child(clientId).child(notificationId)
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d("Notification", "Notification sent to client successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Notification", "Failed to send notification to client: ${e.message}")
            }
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