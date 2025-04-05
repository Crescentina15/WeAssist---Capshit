package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.AppointmentAdapter
import com.remedio.weassist.R

class ClientAppointmentsFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var appointmentList: ArrayList<Appointment>
    private lateinit var emptyAppointmentsLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_client_appointments, container, false)

        // Initialize
        database = FirebaseDatabase.getInstance().reference
        appointmentRecyclerView = view.findViewById(R.id.appointments_recycler_view)
        emptyAppointmentsLayout = view.findViewById(R.id.empty_appointments_layout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        appointmentRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        appointmentList = ArrayList()

        // Fetch the currently logged-in client's UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        val clientId = currentUser?.uid

        if (clientId != null) {
            Log.d("ClientCheck", "Logged in as Client: $clientId")
            // Show loading initially
            showLoading()
            fetchClientAppointments(clientId)
        } else {
            Log.e("ClientCheck", "No logged-in client found.")
            showEmptyState()
        }

        return view
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        appointmentRecyclerView.visibility = View.GONE
        emptyAppointmentsLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        progressIndicator.visibility = View.GONE
        appointmentRecyclerView.visibility = View.GONE
        emptyAppointmentsLayout.visibility = View.VISIBLE
    }

    private fun showAppointments() {
        progressIndicator.visibility = View.GONE
        appointmentRecyclerView.visibility = View.VISIBLE
        emptyAppointmentsLayout.visibility = View.GONE
    }

    private fun updateUiState() {
        if (appointmentList.isEmpty()) {
            showEmptyState()
        } else {
            showAppointments()
        }
    }

    private fun fetchClientAppointments(clientId: String) {
        val appointmentsRef = database.child("appointments")
        Log.d("ClientCheck", "Fetching appointments for clientId: $clientId")

        // First query by clientId, then filter by status
        appointmentsRef.orderByChild("clientId").equalTo(clientId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    appointmentList.clear()
                    if (snapshot.exists()) {
                        val totalAppointments = snapshot.childrenCount
                        var processedAppointments = 0

                        for (child in snapshot.children) {
                            val appointment = child.getValue(Appointment::class.java)
                            // Only process if status is "Accepted"
                            if (appointment != null &&
                                (appointment.status == "Accepted" || appointment.status == "Forwarded")) {
                                // Store the appointment ID from Firebase
                                appointment.appointmentId = child.key ?: ""

                                Log.d("ClientCheck", "Found accepted appointment: ${appointment.fullName}, lawyerId=${appointment.lawyerId}")

                                // Fetch the lawyer's details if not already set
                                if (appointment.lawyerName.isEmpty() || appointment.lawyerProfileImage.isNullOrEmpty()) {
                                    val lawyersRef = database.child("lawyers")
                                    lawyersRef.child(appointment.lawyerId)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                                                val lawyerName = lawyerSnapshot.child("name").value?.toString() ?: "Unknown Lawyer"
                                                val lawyerImage = lawyerSnapshot.child("profileImageUrl").value?.toString() ?: ""

                                                appointment.lawyerName = lawyerName
                                                appointment.lawyerProfileImage = lawyerImage

                                                appointmentList.add(appointment)
                                                Log.d("ClientCheck", "Added appointment with lawyer: $lawyerName, image: $lawyerImage")

                                                // Check if all appointments are processed
                                                processedAppointments++
                                                if (processedAppointments >= totalAppointments) {
                                                    updateAdapter()
                                                    updateUiState()
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                Log.e("ClientCheck", "Error fetching lawyer: ${error.message}")
                                                processedAppointments++
                                                if (processedAppointments >= totalAppointments) {
                                                    updateAdapter()
                                                    updateUiState()
                                                }
                                            }
                                        })
                                } else {
                                    appointmentList.add(appointment)
                                    processedAppointments++
                                    if (processedAppointments >= totalAppointments) {
                                        updateAdapter()
                                        updateUiState()
                                    }
                                }
                            } else {
                                processedAppointments++
                                if (processedAppointments >= totalAppointments) {
                                    updateAdapter()
                                    updateUiState()
                                }
                            }
                        }
                    } else {
                        Log.d("ClientCheck", "No appointments found for client in DB.")
                        updateAdapter() // Update with empty list
                        showEmptyState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ClientCheck", "Error fetching appointments: ${error.message}")
                    showEmptyState()
                }
            })
    }

    // Helper method to update the adapter
    private fun updateAdapter() {
        val sortedList = appointmentList.sortedWith(compareBy { it.status != "Accepted" })

        val adapter = AppointmentAdapter(ArrayList(sortedList), true, true) { selectedAppointment ->
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