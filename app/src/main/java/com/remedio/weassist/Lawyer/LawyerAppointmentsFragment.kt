package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.LawyerAppointmentAdapter
import com.remedio.weassist.R

class LawyerAppointmentsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var appointmentAdapter: LawyerAppointmentAdapter
    private lateinit var appointmentList: MutableList<Appointment>
    private lateinit var databaseRef: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_lawyer_appointments, container, false)

        recyclerView = view.findViewById(R.id.appointments_recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        appointmentList = mutableListOf()

        appointmentAdapter = LawyerAppointmentAdapter(appointmentList) { appointment ->
            val intent = Intent(requireContext(), ConsultationActivity::class.java).apply {
                putExtra("client_name", appointment.fullName)
                putExtra("consultation_time", appointment.time)
                putExtra("appointment_id", appointment.appointmentId)
                putExtra("problem", appointment.problem)
                putExtra("date", appointment.date) // Add date to intent
            }
            startActivity(intent)
        }

        recyclerView.adapter = appointmentAdapter

        // Show loading state initially
        showLoading()

        loadAcceptedAppointments()

        return view
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        progressIndicator.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showAppointments() {
        progressIndicator.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun loadAcceptedAppointments() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            showEmptyState()
            return
        }

        val lawyerId = currentUser.uid
        databaseRef = FirebaseDatabase.getInstance().reference
            .child("lawyers").child(lawyerId).child("appointments")

        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                appointmentList.clear()

                for (appointmentSnapshot in snapshot.children) {
                    try {
                        if (appointmentSnapshot.hasChild("fullName") &&
                            appointmentSnapshot.hasChild("time") &&
                            appointmentSnapshot.hasChild("problem") &&
                            appointmentSnapshot.hasChild("date")) { // Check for date field

                            val appointment = appointmentSnapshot.getValue(Appointment::class.java)
                            appointment?.let {
                                if (appointment.appointmentId.isEmpty()) {
                                    appointment.appointmentId = appointmentSnapshot.key ?: ""
                                }
                                appointmentList.add(it)
                            }
                        } else {
                            Log.w("AppointmentLoad", "Skipping invalid appointment data: ${appointmentSnapshot.key}")
                        }
                    } catch (e: Exception) {
                        Log.e("AppointmentLoad", "Error converting appointment: ${appointmentSnapshot.key}", e)
                    }
                }

                appointmentAdapter.updateAppointments(appointmentList)

                // Show empty state if no appointments, otherwise show RecyclerView
                if (appointmentList.isEmpty()) {
                    showEmptyState()
                } else {
                    showAppointments()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load appointments", Toast.LENGTH_SHORT).show()
                Log.e("AppointmentLoad", "Database error: ${error.message}", error.toException())
                // Show empty state on error
                showEmptyState()
            }
        })
    }
}