package com.remedio.weassist.Secretary

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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.AppointmentAdapter
import com.remedio.weassist.R

class SecretaryAppointmentFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var appointmentList: ArrayList<Appointment>
    private lateinit var lawyerIdList: ArrayList<String>
    private lateinit var adapter: AppointmentAdapter
    private lateinit var deleteDrawable: ColorDrawable
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_appointment, container, false)

        // Initialize
        database = FirebaseDatabase.getInstance().reference
        appointmentRecyclerView = view.findViewById(R.id.appointment_recyclerview)
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        appointmentRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        appointmentList = ArrayList()
        lawyerIdList = ArrayList()

        // Initialize delete background
        deleteDrawable = ColorDrawable(Color.RED)

        // Initialize the adapter
        adapter = AppointmentAdapter(
            appointments = appointmentList,
            isClickable = true,
            isClientView = false,
            onItemClickListener = { selectedAppointment ->
                showAppointmentDetails(selectedAppointment)
            }
        )
        appointmentRecyclerView.adapter = adapter

        // Set up swipe-to-delete functionality
        setupSwipeToDelete()

        // Fetch the currently logged-in secretary's UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        val secretaryId = currentUser?.uid

        if (secretaryId != null) {
            Log.d("SecretaryCheck", "Logged in as Secretary: $secretaryId")
            fetchSecretaryLawFirm(secretaryId)
        } else {
            Log.e("SecretaryCheck", "No logged-in secretary found.")
            showEmptyState()
        }

        return view
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        appointmentRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        progressIndicator.visibility = View.GONE
        appointmentRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showAppointments() {
        progressIndicator.visibility = View.GONE
        appointmentRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun updateUiState() {
        if (appointmentList.isEmpty()) {
            showEmptyState()
        } else {
            showAppointments()
        }
    }

    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false // We don't want drag and drop
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val deletedAppointment = appointmentList[position]

                // Remove from Firebase
                removeAppointment(deletedAppointment)

                // Remove from the list and notify adapter
                appointmentList.removeAt(position)
                adapter.notifyItemRemoved(position)

                // Update UI state
                updateUiState()

                // Show undo option
                val snackbar = Snackbar.make(
                    requireView(),
                    "Appointment for ${deletedAppointment.fullName} removed",
                    Snackbar.LENGTH_LONG
                )

                snackbar.setAction("UNDO") {
                    // Restore the appointment in Firebase
                    restoreAppointment(deletedAppointment)

                    // Re-add to list at the correct position based on status
                    val newPosition = when (deletedAppointment.status?.lowercase()) {
                        "pending" -> 0
                        "accepted" -> {
                            val firstNonPending = appointmentList.indexOfFirst {
                                it.status?.lowercase() != "pending"
                            }
                            if (firstNonPending == -1) appointmentList.size else firstNonPending
                        }
                        else -> appointmentList.size
                    }

                    appointmentList.add(newPosition, deletedAppointment)
                    adapter.notifyItemInserted(newPosition)
                    updateUiState()
                }

                snackbar.show()
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

                // Draw the red delete background
                val background = deleteDrawable
                background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                background.draw(c)

                // Draw delete icon if needed
                val deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)
                deleteIcon?.let {
                    val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                    val iconBottom = iconTop + it.intrinsicHeight
                    val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                    val iconRight = itemView.right - iconMargin

                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(appointmentRecyclerView)
    }

    private fun removeAppointment(appointment: Appointment) {
        val appointmentRef = database.child("appointments").child(appointment.appointmentId)

        appointmentRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    database.child("deletedAppointments")
                        .child(appointment.appointmentId)
                        .setValue(snapshot.value)
                        .addOnSuccessListener {
                            appointmentRef.removeValue()
                                .addOnSuccessListener {
                                    Log.d("AppointmentRemoval", "Appointment successfully removed: ${appointment.appointmentId}")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("AppointmentRemoval", "Error removing appointment: ${e.message}")
                                }
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppointmentRemoval", "Database error: ${error.message}")
            }
        })
    }

    private fun restoreAppointment(appointment: Appointment) {
        database.child("deletedAppointments").child(appointment.appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        database.child("appointments")
                            .child(appointment.appointmentId)
                            .setValue(snapshot.value)
                            .addOnSuccessListener {
                                database.child("deletedAppointments")
                                    .child(appointment.appointmentId)
                                    .removeValue()

                                Log.d("AppointmentRestore", "Appointment successfully restored: ${appointment.appointmentId}")
                            }
                            .addOnFailureListener { e ->
                                Log.e("AppointmentRestore", "Error restoring appointment: ${e.message}")
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("AppointmentRestore", "Database error: ${error.message}")
                }
            })
    }

    private fun fetchSecretaryLawFirm(secretaryId: String) {
        showLoading()
        val secretaryRef = database.child("secretaries").child(secretaryId)
        secretaryRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawFirm = snapshot.child("lawFirm").getValue(String::class.java).orEmpty()
                    Log.d("SecretaryCheck", "Secretary's lawFirm: $lawFirm")
                    fetchLawyersForLawFirm(lawFirm)
                } else {
                    Log.e("SecretaryCheck", "Secretary data not found in DB for ID: $secretaryId")
                    showEmptyState()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryCheck", "Error fetching secretary data: ${error.message}")
                showEmptyState()
            }
        })
    }

    private fun fetchLawyersForLawFirm(lawFirm: String) {
        val lawyersRef = database.child("lawyers")
        lawyersRef.orderByChild("lawFirm").equalTo(lawFirm)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        lawyerIdList.clear()
                        for (child in snapshot.children) {
                            val lawyerKey = child.key
                            if (!lawyerKey.isNullOrEmpty()) {
                                lawyerIdList.add(lawyerKey)
                                Log.d("SecretaryCheck", "Found lawyer with ID: $lawyerKey")
                            }
                        }
                        fetchAppointmentsForLawyers(lawyerIdList)
                        listenForAppointmentChanges(lawyerIdList)
                    } else {
                        Log.d("SecretaryCheck", "No lawyers found for lawFirm: $lawFirm")
                        showEmptyState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryCheck", "Error fetching lawyers: ${error.message}")
                    showEmptyState()
                }
            })
    }

    private fun fetchAppointmentsForLawyers(lawyerIds: List<String>) {
        val appointmentsRef = database.child("appointments")
        val lawyersRef = database.child("lawyers")
        Log.d("SecretaryCheck", "Fetching appointments for lawyerIds: $lawyerIds")

        lawyersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(lawyersSnapshot: DataSnapshot) {
                val lawyerDetails = mutableMapOf<String, Pair<String, String?>>()
                for (lawyerSnapshot in lawyersSnapshot.children) {
                    val lawyerId = lawyerSnapshot.key ?: continue
                    val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    val lawyerProfileImage = lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)
                    lawyerDetails[lawyerId] = Pair(lawyerName, lawyerProfileImage)
                }

                appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        appointmentList.clear()
                        if (snapshot.exists()) {
                            val pendingAppointments = mutableListOf<Appointment>()
                            val acceptedAppointments = mutableListOf<Appointment>()
                            val forwardedAppointments = mutableListOf<Appointment>()

                            for (child in snapshot.children) {
                                val appointment = child.getValue(Appointment::class.java)
                                val clientId = child.child("clientId").getValue(String::class.java) ?: "Unknown"

                                if (appointment != null && appointment.lawyerId in lawyerIds) {
                                    val updatedAppointment = appointment.copy(
                                        appointmentId = child.key ?: "Unknown",
                                        clientId = clientId,
                                        lawyerName = lawyerDetails[appointment.lawyerId]?.first ?: "Unknown Lawyer",
                                        lawyerProfileImage = lawyerDetails[appointment.lawyerId]?.second
                                    )

                                    when (updatedAppointment.status?.lowercase()) {
                                        "pending" -> pendingAppointments.add(updatedAppointment)
                                        "accepted" -> acceptedAppointments.add(updatedAppointment)
                                        else -> forwardedAppointments.add(updatedAppointment)
                                    }
                                }
                            }

                            // Combine in order: pending -> accepted -> forwarded
                            appointmentList.addAll(pendingAppointments)
                            appointmentList.addAll(acceptedAppointments)
                            appointmentList.addAll(forwardedAppointments)

                            adapter.updateAppointments(appointmentList)
                            updateUiState()
                        } else {
                            Log.d("SecretaryCheck", "No appointments found in DB.")
                            showEmptyState()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("SecretaryCheck", "Error fetching appointments: ${error.message}")
                        showEmptyState()
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryCheck", "Error fetching lawyers: ${error.message}")
                showEmptyState()
            }
        })
    }

    private fun listenForAppointmentChanges(lawyerIds: List<String>) {
        val appointmentsRef = database.child("appointments")
        appointmentsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val appointment = snapshot.getValue(Appointment::class.java)
                if (appointment != null && appointment.lawyerId in lawyerIds) {
                    appointment.appointmentId = snapshot.key ?: "Unknown"

                    val lawyersRef = database.child("lawyers").child(appointment.lawyerId)
                    lawyersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                            val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                            val lawyerProfileImage = lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)

                            val updatedAppointment = appointment.copy(
                                lawyerName = lawyerName,
                                lawyerProfileImage = lawyerProfileImage
                            )

                            val position = calculatePositionForStatus(updatedAppointment.status)
                            appointmentList.add(position, updatedAppointment)
                            adapter.notifyItemInserted(position)
                            updateUiState()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("SecretaryCheck", "Error fetching lawyer details: ${error.message}")
                        }
                    })
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val appointment = snapshot.getValue(Appointment::class.java)
                if (appointment != null && appointment.lawyerId in lawyerIds) {
                    appointment.appointmentId = snapshot.key ?: "Unknown"

                    val lawyersRef = database.child("lawyers").child(appointment.lawyerId)
                    lawyersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                            val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                            val lawyerProfileImage = lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)

                            val updatedAppointment = appointment.copy(
                                lawyerName = lawyerName,
                                lawyerProfileImage = lawyerProfileImage
                            )

                            val oldPosition = appointmentList.indexOfFirst {
                                it.appointmentId == updatedAppointment.appointmentId
                            }

                            if (oldPosition != -1) {
                                appointmentList.removeAt(oldPosition)
                                adapter.notifyItemRemoved(oldPosition)

                                val newPosition = calculatePositionForStatus(updatedAppointment.status)
                                appointmentList.add(newPosition, updatedAppointment)
                                adapter.notifyItemInserted(newPosition)
                                updateUiState()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("SecretaryCheck", "Error fetching lawyer details: ${error.message}")
                        }
                    })
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val appointment = snapshot.getValue(Appointment::class.java)
                if (appointment != null && appointment.lawyerId in lawyerIds) {
                    appointment.appointmentId = snapshot.key ?: "Unknown"
                    appointmentList.removeAll { it.appointmentId == appointment.appointmentId }
                    adapter.updateAppointments(appointmentList)
                    updateUiState()
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not implemented
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryCheck", "Error listening for appointment changes: ${error.message}")
                updateUiState()
            }
        })
    }

    private fun calculatePositionForStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "pending" -> 0
            "accepted" -> {
                val firstNonPending = appointmentList.indexOfFirst {
                    it.status?.lowercase() != "pending"
                }
                if (firstNonPending == -1) appointmentList.size else firstNonPending
            }
            else -> appointmentList.size
        }
    }

    private fun showAppointmentDetails(appointment: Appointment) {
        val intent = Intent(requireContext(), SecretaryAppointmentDetailsActivity::class.java)
        intent.putExtra("APPOINTMENT_ID", appointment.appointmentId)
        startActivity(intent)
    }
}