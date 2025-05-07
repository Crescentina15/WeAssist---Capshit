package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.AppointmentAdapter
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ClientAppointmentsFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var appointmentRecyclerView: RecyclerView

    private lateinit var emptyAppointmentsLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var adapter: AppointmentAdapter
    private var appointmentList = mutableListOf<Appointment>()

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
                            // Only process if status is "Accepted", "Forwarded", or "Complete"
                            if (appointment != null &&
                                (appointment.status == "Accepted" ||
                                        appointment.status == "Forwarded" ||
                                        appointment.status == "Complete")) {

                                // Store the appointment ID from Firebase
                                appointment.appointmentId = child.key ?: ""

                                Log.d("ClientCheck", "Found appointment: ${appointment.fullName}, status=${appointment.status}")

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
                                                    sortAppointmentsByDateTime()
                                                    updateAdapter()
                                                    updateUiState()
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                Log.e("ClientCheck", "Error fetching lawyer: ${error.message}")
                                                processedAppointments++
                                                if (processedAppointments >= totalAppointments) {
                                                    sortAppointmentsByDateTime()
                                                    updateAdapter()
                                                    updateUiState()
                                                }
                                            }
                                        })
                                } else {
                                    appointmentList.add(appointment)
                                    processedAppointments++
                                    if (processedAppointments >= totalAppointments) {
                                        sortAppointmentsByDateTime()
                                        updateAdapter()
                                        updateUiState()
                                    }
                                }
                            } else {
                                processedAppointments++
                                if (processedAppointments >= totalAppointments) {
                                    sortAppointmentsByDateTime()
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

    private fun sortAppointmentsByDateTime() {
        val dateTimeFormat = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.getDefault())

        appointmentList.sortWith(compareByDescending<Appointment> { appointment ->
            try {
                // Combine date and time for accurate sorting
                val dateTimeString = "${appointment.date} ${appointment.time}"
                dateTimeFormat.parse(dateTimeString)?.time ?: 0L
            } catch (e: Exception) {
                Log.e("SortError", "Error parsing date/time: ${e.message}")
                0L
            }
        })
    }

    private fun updateAdapter() {
        if (!::adapter.isInitialized) {
            adapter = AppointmentAdapter(appointmentList, true, true) { selectedAppointment ->
                // Only allow clicking on non-completed appointments
                if (selectedAppointment.status != "Complete") {
                    showAppointmentDetails(selectedAppointment)
                }
            }
            appointmentRecyclerView.adapter = adapter

            val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val appointmentToDelete = appointmentList[position]
                    val appointmentId = appointmentToDelete.appointmentId

                    // Don't allow deleting completed appointments
                    if (appointmentToDelete.status == "Complete") {
                        adapter.notifyItemChanged(position)
                        Toast.makeText(requireContext(), "Completed appointments cannot be deleted", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (appointmentId.isNotEmpty()) {
                        database.child("appointments").child(appointmentId).removeValue()
                            .addOnSuccessListener {
                                appointmentList.removeAt(position)
                                adapter.notifyItemRemoved(position)
                                updateUiState()
                                Toast.makeText(requireContext(), "Appointment has been removed", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Log.e("SwipeDelete", "Failed to delete appointment: ${it.message}")
                                adapter.notifyItemChanged(position)
                                Toast.makeText(requireContext(), "Failed to delete appointment", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }

            ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(appointmentRecyclerView)
        } else {
            adapter.updateAppointments(appointmentList)
        }
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

    // Add a real-time listener for appointment status changes
    override fun onResume() {
        super.onResume()

        // Set up real-time listener for appointment updates
        val currentUser = FirebaseAuth.getInstance().currentUser
        val clientId = currentUser?.uid

        if (clientId != null) {
            setupAppointmentListener(clientId)
        }
    }

    private lateinit var appointmentListener: ValueEventListener

    private fun setupAppointmentListener(clientId: String) {
        val appointmentsRef = database.child("appointments").orderByChild("clientId").equalTo(clientId)

        appointmentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Only refresh if we already have appointments loaded
                if (::adapter.isInitialized) {
                    fetchClientAppointments(clientId)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppointmentListener", "Database error: ${error.message}")
            }
        }

        appointmentsRef.addValueEventListener(appointmentListener)
    }

    override fun onPause() {
        super.onPause()

        // Remove listener to prevent memory leaks
        if (::appointmentListener.isInitialized) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val clientId = currentUser?.uid

            if (clientId != null) {
                val appointmentsRef = database.child("appointments").orderByChild("clientId").equalTo(clientId)
                appointmentsRef.removeEventListener(appointmentListener)
            }
        }
    }
}