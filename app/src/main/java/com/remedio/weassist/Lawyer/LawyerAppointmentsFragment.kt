package com.remedio.weassist.Lawyer

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.LawyerAppointmentAdapter
import com.remedio.weassist.R
import java.util.UUID

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

        appointmentAdapter = LawyerAppointmentAdapter(
            appointmentList,
            { appointment ->
                val intent = Intent(requireContext(), ConsultationActivity::class.java).apply {
                    putExtra("client_name", appointment.fullName)
                    putExtra("consultation_time", appointment.time)
                    putExtra("appointment_id", appointment.appointmentId)
                    putExtra("problem", appointment.problem)
                    putExtra("date", appointment.date)
                }
                startActivity(intent)
            },
            { appointment ->
                // Handle end session
                endSession(appointment)
            }
        )

        recyclerView.adapter = appointmentAdapter

        // Setup swipe-to-delete
        setupSwipeToDelete()

        // Show loading state initially
        showLoading()

        loadAcceptedAppointments()

        setupSessionListener()

        return view
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            private val deleteBackground = ColorDrawable(Color.RED)
            private val deleteIcon = ContextCompat.getDrawable(
                requireContext(),
                android.R.drawable.ic_menu_delete
            )

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // We're not handling drag & drop
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val appointment = appointmentList[position]

                // Show confirmation dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Appointment")
                    .setMessage("Are you sure you want to delete this appointment with ${appointment.fullName}?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteAppointment(appointment)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        // Restore the item if the user cancels
                        appointmentAdapter.notifyItemChanged(position)
                    }
                    .setCancelable(false)
                    .show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView

                // Calculate icon dimensions
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2
                val iconLeft = itemView.right - iconMargin - (deleteIcon?.intrinsicWidth ?: 0)
                val iconRight = itemView.right - iconMargin
                val iconTop = itemView.top + iconMargin
                val iconBottom = itemView.bottom - iconMargin

                // Draw the red delete background
                deleteBackground.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                deleteBackground.draw(c)

                // Draw the delete icon if we've swiped far enough
                if (dX < -iconMargin) {
                    deleteIcon?.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    deleteIcon?.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun deleteAppointment(appointment: Appointment) {
        val currentUser = auth.currentUser ?: return
        val lawyerId = currentUser.uid

        // Remove from lawyer's appointments
        val appointmentRef = FirebaseDatabase.getInstance().reference
            .child("lawyers").child(lawyerId).child("appointments")
            .child(appointment.appointmentId)

        // Also remove from active_sessions if it exists there
        val activeSessionRef = FirebaseDatabase.getInstance().reference
            .child("lawyers").child(lawyerId).child("active_sessions")
            .child(appointment.appointmentId)

        // Also remove from accepted_appointment
        val acceptedAppointmentRef = FirebaseDatabase.getInstance().reference
            .child("accepted_appointment").child(appointment.appointmentId)

        // Create a map of updates to perform all deletions in one transaction
        val updates = hashMapOf<String, Any?>(
            "/lawyers/$lawyerId/appointments/${appointment.appointmentId}" to null,
            "/lawyers/$lawyerId/active_sessions/${appointment.appointmentId}" to null,
            "/accepted_appointment/${appointment.appointmentId}" to null
        )

        // Create a notification for the client
        val notificationData = hashMapOf<String, Any>(
            "type" to "appointment_cancelled",
            "message" to "Your appointment has been cancelled by the lawyer",
            "timestamp" to ServerValue.TIMESTAMP,
            "isRead" to false
        )

        // Add notification to the updates
        updates["/notifications/${appointment.clientId}/${appointment.appointmentId}"] = notificationData

        // Perform all updates in one transaction
        FirebaseDatabase.getInstance().reference.updateChildren(updates)
            .addOnSuccessListener {
                // Remove from local list and update adapter
                appointmentList.remove(appointment)
                appointmentAdapter.removeAppointment(appointment.appointmentId)

                // Show empty state if needed
                if (appointmentList.isEmpty()) {
                    showEmptyState()
                }

                Toast.makeText(requireContext(), "Appointment deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to delete appointment", Toast.LENGTH_SHORT).show()
                Log.e("AppointmentDelete", "Error deleting appointment", e)
                // Make sure the UI is consistent
                loadAcceptedAppointments()
            }
    }

    private fun setupSessionListener() {
        val currentUser = auth.currentUser ?: return
        val lawyerId = currentUser.uid
        val sessionRef = FirebaseDatabase.getInstance().reference
            .child("lawyers").child(lawyerId).child("active_sessions")

        sessionRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val appointmentId = snapshot.key ?: return
                appointmentAdapter.setSessionActive(appointmentId, true)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle changes if needed
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val appointmentId = snapshot.key ?: return
                appointmentAdapter.setSessionActive(appointmentId, false)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("SessionListener", "Error listening to session changes", error.toException())
            }
        })
    }

    private fun endSession(appointment: Appointment) {
        val currentUser = auth.currentUser ?: return
        val lawyerId = currentUser.uid

        // First, fetch the appointment to get any attachments
        FirebaseDatabase.getInstance().reference
            .child("lawyers").child(lawyerId).child("appointments")
            .child(appointment.appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Get the attachments list if it exists
                    val attachments = if (snapshot.child("attachments").exists()) {
                        try {
                            snapshot.child("attachments").getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                        } catch (e: Exception) {
                            Log.e("EndSession", "Error parsing attachments: ${e.message}")
                            emptyList<String>()
                        }
                    } else {
                        emptyList<String>()
                    }

                    // Create more detailed rating notification
                    val ratingNotification = hashMapOf<String, Any>(
                        "lawyerId" to lawyerId,
                        "lawyerName" to (currentUser.displayName ?: "Lawyer"),
                        "appointmentId" to appointment.appointmentId,
                        "appointmentDate" to appointment.date,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "clientId" to appointment.clientId
                    )

                    val notificationData = hashMapOf<String, Any>(
                        "type" to "rating_request",
                        "message" to "Please rate your recent consultation",
                        "timestamp" to ServerValue.TIMESTAMP,
                        "isRead" to false
                    )

                    // Create a consultation record with details and files
                    val consultationId = FirebaseDatabase.getInstance().reference
                        .child("consultations")
                        .child(appointment.clientId)
                        .push().key ?: UUID.randomUUID().toString()

                    val consultation = hashMapOf<String, Any>(
                        "clientName" to appointment.fullName,
                        "lawyerId" to lawyerId,
                        "consultationDate" to appointment.date,
                        "consultationTime" to appointment.time,
                        "notes" to "Consultation completed",
                        "problem" to (appointment.problem ?: ""),
                        "status" to "Complete",
                        "appointmentId" to appointment.appointmentId
                    )

                    // Prepare all database updates in a single transaction
                    val updates = hashMapOf<String, Any>(
                        // Store consultation record
                        "/consultations/${appointment.clientId}/$consultationId" to consultation,

                        // Store rating notification
                        "/pending_ratings/${appointment.clientId}/${appointment.appointmentId}" to ratingNotification,
                        "/notifications/${appointment.clientId}/${appointment.appointmentId}" to notificationData
                    )

                    // If we have attachments, add them to the completed appointment
                    if (attachments.isNotEmpty()) {
                        // Add attachments to the client's completed appointment data
                        updates["/appointments/${appointment.appointmentId}/attachments"] = attachments
                    }

                    // Perform all updates in one transaction
                    FirebaseDatabase.getInstance().reference.updateChildren(updates)
                        .addOnSuccessListener {
                            // Proceed with ending the session
                            FirebaseDatabase.getInstance().reference
                                .child("lawyers").child(lawyerId).child("active_sessions")
                                .child(appointment.appointmentId)
                                .removeValue()
                                .addOnSuccessListener {
                                    val sessionUpdates = hashMapOf<String, Any?>(
                                        "/lawyers/$lawyerId/appointments/${appointment.appointmentId}" to null,
                                        "/accepted_appointment/${appointment.appointmentId}" to null
                                    )

                                    FirebaseDatabase.getInstance().reference.updateChildren(sessionUpdates)
                                        .addOnSuccessListener {
                                            Toast.makeText(requireContext(),
                                                "Session ended successfully",
                                                Toast.LENGTH_SHORT).show()

                                            appointmentAdapter.removeAppointment(appointment.appointmentId)
                                            appointmentList.removeAll { it.appointmentId == appointment.appointmentId }

                                            // Check if we need to show empty state
                                            if (appointmentList.isEmpty()) {
                                                showEmptyState()
                                            }
                                        }
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(),
                                "Failed to create rating request",
                                Toast.LENGTH_SHORT).show()
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("EndSession", "Error fetching appointment attachments: ${error.message}")
                    // Fallback to original behavior
                    endSessionOriginal(appointment)
                }
            })
    }

    private fun endSessionOriginal(appointment: Appointment) {
        val currentUser = auth.currentUser ?: return
        val lawyerId = currentUser.uid

        // Create more detailed rating notification
        val ratingNotification = hashMapOf<String, Any>(
            "lawyerId" to lawyerId,
            "lawyerName" to (currentUser.displayName ?: "Lawyer"),
            "appointmentId" to appointment.appointmentId,
            "appointmentDate" to appointment.date,
            "timestamp" to ServerValue.TIMESTAMP,
            "clientId" to appointment.clientId
        )

        val notificationData = hashMapOf<String, Any>(
            "type" to "rating_request",
            "message" to "Please rate your recent consultation",
            "timestamp" to ServerValue.TIMESTAMP,
            "isRead" to false
        )

        // Store under both client's pending ratings and general notifications
        val updates = hashMapOf<String, Any>(
            "/pending_ratings/${appointment.clientId}/${appointment.appointmentId}" to ratingNotification,
            "/notifications/${appointment.clientId}/${appointment.appointmentId}" to notificationData
        )

        FirebaseDatabase.getInstance().reference.updateChildren(updates)
            .addOnSuccessListener {
                // Original flow continues...
                FirebaseDatabase.getInstance().reference
                    .child("lawyers").child(lawyerId).child("active_sessions")
                    .child(appointment.appointmentId)
                    .removeValue()
                    .addOnSuccessListener {
                        val sessionUpdates = hashMapOf<String, Any?>(
                            "/lawyers/$lawyerId/appointments/${appointment.appointmentId}" to null,
                            "/accepted_appointment/${appointment.appointmentId}" to null
                        )

                        FirebaseDatabase.getInstance().reference.updateChildren(sessionUpdates)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(),
                                    "Session ended successfully",
                                    Toast.LENGTH_SHORT).show()

                                appointmentAdapter.removeAppointment(appointment.appointmentId)
                                appointmentList.removeAll { it.appointmentId == appointment.appointmentId }

                                // Check if we need to show empty state
                                if (appointmentList.isEmpty()) {
                                    showEmptyState()
                                }
                            }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(),
                    "Failed to create rating request",
                    Toast.LENGTH_SHORT).show()
            }
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