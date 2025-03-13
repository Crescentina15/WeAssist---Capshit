package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.AppointmentAdapter
import com.remedio.weassist.R

class ClientAppointmentsFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var appointmentList: ArrayList<Appointment>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_client_appointments, container, false)

        // Initialize
        database = FirebaseDatabase.getInstance().reference
        appointmentRecyclerView = view.findViewById(R.id.appointments_recycler_view)
        appointmentRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        appointmentList = ArrayList()

        // Fetch the currently logged-in client's UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        val clientId = currentUser?.uid

        if (clientId != null) {
            Log.d("ClientCheck", "Logged in as Client: $clientId")
            fetchAcceptedAppointments(clientId)
        } else {
            Log.e("ClientCheck", "No logged-in client found.")
        }

        return view
    }

    private fun fetchAcceptedAppointments(clientId: String) {
        val acceptedAppointmentsRef = database.child("accepted_appointment")
        Log.d("ClientCheck", "Fetching accepted appointments for clientId: $clientId")

        acceptedAppointmentsRef.orderByChild("clientId").equalTo(clientId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    appointmentList.clear()
                    if (snapshot.exists()) {
                        val totalAppointments = snapshot.childrenCount
                        var processedAppointments = 0

                        for (child in snapshot.children) {
                            val appointment = child.getValue(Appointment::class.java)
                            if (appointment != null) {
                                // Store the appointment ID from Firebase
                                appointment.appointmentId = child.key ?: ""

                                Log.d("ClientCheck", "Found appointment: ${appointment.fullName}, lawyerId=${appointment.lawyerId}")

                                // Fetch the lawyer's name if not already set
                                if (appointment.lawyerName.isEmpty()) {
                                    val lawyersRef = database.child("lawyers")
                                    lawyersRef.child(appointment.lawyerId)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                                                val lawyerName = lawyerSnapshot.child("name").value?.toString() ?: "Unknown Lawyer"
                                                appointment.lawyerName = lawyerName

                                                appointmentList.add(appointment)
                                                Log.d("ClientCheck", "Added appointment with lawyer: $lawyerName")

                                                // Check if all appointments are processed
                                                processedAppointments++
                                                if (processedAppointments >= totalAppointments) {
                                                    updateAdapter()
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                Log.e("ClientCheck", "Error fetching lawyer: ${error.message}")
                                                processedAppointments++
                                                if (processedAppointments >= totalAppointments) {
                                                    updateAdapter()
                                                }
                                            }
                                        })
                                } else {
                                    appointmentList.add(appointment)
                                    processedAppointments++
                                    if (processedAppointments >= totalAppointments) {
                                        updateAdapter()
                                    }
                                }
                            } else {
                                processedAppointments++
                            }
                        }

                        // If there are no appointments or all were null, update adapter anyway
                        if (totalAppointments == 0L || processedAppointments >= totalAppointments) {
                            updateAdapter()
                        }
                    } else {
                        Log.d("ClientCheck", "No accepted appointments found in DB.")
                        updateAdapter() // Update with empty list
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ClientCheck", "Error fetching accepted appointments: ${error.message}")
                }
            })
    }

    // Helper method to update the adapter
    private fun updateAdapter() {
        val adapter = AppointmentAdapter(appointmentList, true, true) { selectedAppointment ->
            showAppointmentDetails(selectedAppointment)
        }
        appointmentRecyclerView.adapter = adapter
    }

    private fun showAppointmentDetails(appointment: Appointment) {
        // Start the ClientAppointmentDetailsActivity
        val intent = Intent(requireContext(), ClientAppointmentDetailsActivity::class.java)

        // Pass appointment ID and other relevant details
        intent.putExtra("APPOINTMENT_ID", appointment.appointmentId)
        intent.putExtra("LAWYER_NAME", appointment.lawyerName)
        intent.putExtra("DATE", appointment.date)
        intent.putExtra("TIME", appointment.time)
        intent.putExtra("PROBLEM", appointment.problem)
        intent.putExtra("STATUS", appointment.status)
        intent.putExtra("FULL_NAME", appointment.fullName)
        intent.putExtra("LAWYER_PROFILE_IMAGE", appointment.lawyerProfileImage)

        startActivity(intent)
    }
}