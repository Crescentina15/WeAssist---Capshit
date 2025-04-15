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
import com.remedio.weassist.Utils.ConversationUtils
import java.util.UUID

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
        val swipeToDeleteCallback =
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
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
                    val deleteIcon = ContextCompat.getDrawable(
                        requireContext(),
                        android.R.drawable.ic_menu_delete
                    )
                    deleteIcon?.let {
                        val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                        val iconBottom = iconTop + it.intrinsicHeight
                        val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                        val iconRight = itemView.right - iconMargin

                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.draw(c)
                    }

                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
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
                                    Log.d(
                                        "AppointmentRemoval",
                                        "Appointment successfully removed: ${appointment.appointmentId}"
                                    )
                                }
                                .addOnFailureListener { e ->
                                    Log.e(
                                        "AppointmentRemoval",
                                        "Error removing appointment: ${e.message}"
                                    )
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

                                Log.d(
                                    "AppointmentRestore",
                                    "Appointment successfully restored: ${appointment.appointmentId}"
                                )
                            }
                            .addOnFailureListener { e ->
                                Log.e(
                                    "AppointmentRestore",
                                    "Error restoring appointment: ${e.message}"
                                )
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
                    val lawyerName =
                        lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    val lawyerProfileImage =
                        lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)
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
                                val clientId = child.child("clientId").getValue(String::class.java)
                                    ?: "Unknown"

                                if (appointment != null && appointment.lawyerId in lawyerIds) {
                                    val updatedAppointment = appointment.copy(
                                        appointmentId = child.key ?: "Unknown",
                                        clientId = clientId,
                                        lawyerName = lawyerDetails[appointment.lawyerId]?.first
                                            ?: "Unknown Lawyer",
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
                            val lawyerName =
                                lawyerSnapshot.child("name").getValue(String::class.java)
                                    ?: "Unknown"
                            val lawyerProfileImage =
                                lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)

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
                            Log.e(
                                "SecretaryCheck",
                                "Error fetching lawyer details: ${error.message}"
                            )
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
                            val lawyerName =
                                lawyerSnapshot.child("name").getValue(String::class.java)
                                    ?: "Unknown"
                            val lawyerProfileImage =
                                lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)

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

                                val newPosition =
                                    calculatePositionForStatus(updatedAppointment.status)
                                appointmentList.add(newPosition, updatedAppointment)
                                adapter.notifyItemInserted(newPosition)
                                updateUiState()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e(
                                "SecretaryCheck",
                                "Error fetching lawyer details: ${error.message}"
                            )
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

    // New function to handle forwarding conversations from appointment details
    fun forwardAppointmentToLawyer(appointment: Appointment) {
        val loadingSnackbar = Snackbar.make(
            requireView(),
            "Processing forwarding request...",
            Snackbar.LENGTH_INDEFINITE
        )
        loadingSnackbar.show()

        // 1. First, update the appointment status to "Forwarded"
        val appointmentRef = database.child("appointments").child(appointment.appointmentId)
        appointmentRef.child("status").setValue("Forwarded").addOnSuccessListener {

            // Update status in accepted_appointment node as well
            database.child("accepted_appointment").child(appointment.appointmentId)
                .child("status").setValue("Forwarded")

            // 2. Find the conversation between secretary and client
            findSecretaryClientConversation(appointment.clientId) { secretaryConversationId ->
                if (secretaryConversationId == null) {
                    loadingSnackbar.dismiss()
                    Snackbar.make(
                        requireView(),
                        "No active conversation found for this appointment",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@findSecretaryClientConversation
                }

                // 3. Create a new conversation between lawyer and client
                val lawyerId = appointment.lawyerId
                val clientId = appointment.clientId
                val newConversationId = ConversationUtils.generateConversationId(clientId, lawyerId)

                // 4. Extract problem description from original conversation
                ConversationUtils.extractProblemFromConversation(
                    database,
                    secretaryConversationId
                ) { problemDescription ->
                    // 5. Mark secretary conversation as forwarded
                    closeSecretaryClientConversation(
                        secretaryConversationId,
                        appointment,
                        problemDescription
                    ) {
                        // 6. Create lawyer-client conversation
                        createLawyerClientConversation(
                            appointment,
                            secretaryConversationId,
                            newConversationId,
                            problemDescription
                        ) {
                            // 7. Create notifications
                            createForwardingNotifications(
                                appointment,
                                secretaryConversationId,
                                newConversationId,
                                problemDescription
                            )

                            loadingSnackbar.dismiss()
                            Snackbar.make(
                                requireView(),
                                "Appointment successfully forwarded to lawyer",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }.addOnFailureListener { e ->
            loadingSnackbar.dismiss()
            Log.e("SecretaryAppointment", "Error updating appointment status: ${e.message}")
            Snackbar.make(
                requireView(),
                "Failed to forward appointment",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun findSecretaryClientConversation(clientId: String, callback: (String?) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return callback(null)

        // Generate the expected conversation ID
        val expectedConversationId =
            ConversationUtils.generateConversationId(currentUserId, clientId)

        // Check if conversation exists
        database.child("conversations").child(expectedConversationId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        callback(expectedConversationId)
                    } else {
                        // If not found by ID, search all conversations for this client
                        searchAllConversationsForClient(clientId, currentUserId, callback)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Error finding conversation: ${error.message}")
                    callback(null)
                }
            })
    }

    private fun searchAllConversationsForClient(
        clientId: String,
        secretaryId: String,
        callback: (String?) -> Unit
    ) {
        database.child("conversations")
            .orderByChild("participantIds/$secretaryId")
            .equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundConversationId: String? = null

                    for (conversationSnapshot in snapshot.children) {
                        // Check if client is a participant
                        if (conversationSnapshot.child("participantIds/$clientId").exists() &&
                            conversationSnapshot.child("participantIds/$clientId")
                                .getValue(Boolean::class.java) == true
                        ) {
                            foundConversationId = conversationSnapshot.key
                            break
                        }
                    }

                    callback(foundConversationId)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryAppointment", "Error searching conversations: ${error.message}")
                    callback(null)
                }
            })
    }

    private fun closeSecretaryClientConversation(
        conversationId: String,
        appointment: Appointment,
        problemDescription: String,
        callback: () -> Unit
    ) {
        val secretaryConversationRef = database.child("conversations").child(conversationId)

        // Update status flags
        val updates = mapOf(
            "forwarded" to true,
            "secretaryActive" to false,
            "forwardedToLawyerId" to appointment.lawyerId,
            "forwardedAt" to ServerValue.TIMESTAMP,
            "appointmentId" to appointment.appointmentId
        )

        secretaryConversationRef.updateChildren(updates).addOnSuccessListener {
            // Add a system message
            val systemMessage = mapOf(
                "senderId" to "system",
                "message" to "This conversation has been forwarded to Atty. ${appointment.lawyerName}. " +
                        "The client can now communicate directly with the lawyer.",
                "timestamp" to ServerValue.TIMESTAMP
            )

            secretaryConversationRef.child("messages").push().setValue(systemMessage)
                .addOnSuccessListener {
                    callback()
                }
                .addOnFailureListener { e ->
                    Log.e("SecretaryAppointment", "Error adding system message: ${e.message}")
                    callback() // Still proceed with the rest
                }
        }.addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error closing secretary conversation: ${e.message}")
            callback() // Still proceed with the rest
        }
    }

    private fun createLawyerClientConversation(
        appointment: Appointment,
        originalConversationId: String,
        newConversationId: String,
        problemDescription: String,
        callback: () -> Unit
    ) {
        val lawyerId = appointment.lawyerId
        val clientId = appointment.clientId
        val newConversationRef = database.child("conversations").child(newConversationId)

        // Check if conversation already exists
        newConversationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Update existing conversation
                    val updates = mapOf(
                        "originalConversationId" to originalConversationId,
                        "handledByLawyer" to true,
                        "forwardedFromSecretary" to true,
                        "participantIds/$lawyerId" to true,
                        "participantIds/$clientId" to true,
                        "appointmentId" to appointment.appointmentId,
                        "problem" to problemDescription,
                        "createdAt" to ServerValue.TIMESTAMP
                    )

                    newConversationRef.updateChildren(updates).addOnSuccessListener {
                        // Add a system message
                        addSystemAndWelcomeMessages(
                            newConversationRef,
                            lawyerId,
                            clientId,
                            appointment,
                            problemDescription,
                            callback
                        )
                    }.addOnFailureListener { e ->
                        Log.e(
                            "SecretaryAppointment",
                            "Error updating lawyer conversation: ${e.message}"
                        )
                        callback()
                    }
                } else {
                    // Create new conversation
                    val conversationData = mapOf(
                        "participantIds" to mapOf(
                            lawyerId to true,
                            clientId to true
                        ),
                        "unreadMessages" to mapOf(
                            clientId to 1,  // Client has 1 unread message
                            lawyerId to 0   // Lawyer has 0 unread messages initially
                        ),
                        "appointedLawyerId" to lawyerId,
                        "originalConversationId" to originalConversationId,
                        "handledByLawyer" to true,
                        "forwardedFromSecretary" to true,
                        "createdAt" to ServerValue.TIMESTAMP,
                        "appointmentId" to appointment.appointmentId,
                        "problem" to problemDescription
                    )

                    newConversationRef.setValue(conversationData).addOnSuccessListener {
                        // Add a system message
                        addSystemAndWelcomeMessages(
                            newConversationRef,
                            lawyerId,
                            clientId,
                            appointment,
                            problemDescription,
                            callback
                        )
                    }.addOnFailureListener { e ->
                        Log.e(
                            "SecretaryAppointment",
                            "Error creating lawyer conversation: ${e.message}"
                        )
                        callback()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(
                    "SecretaryAppointment",
                    "Error checking lawyer conversation: ${error.message}"
                )
                callback()
            }
        })
    }

    private fun addSystemAndWelcomeMessages(
        conversationRef: DatabaseReference,
        lawyerId: String,
        clientId: String,
        appointment: Appointment,
        problemDescription: String,
        callback: () -> Unit
    ) {
        // Add system message
        val systemMessage = mapOf(
            "senderId" to "system",
            "message" to "This is a forwarded conversation from a secretary. Original problem: $problemDescription",
            "timestamp" to ServerValue.TIMESTAMP
        )

        conversationRef.child("messages").push().setValue(systemMessage).addOnSuccessListener {
            // Add welcome message from lawyer
            val welcomeMessage = mapOf(
                "message" to "Hello ${appointment.fullName}, I'm Atty. ${appointment.lawyerName}. I've been assigned to provide you with legal assistance.",
                "senderId" to lawyerId,
                "receiverId" to clientId,
                "timestamp" to (System.currentTimeMillis() + 1) // Ensure this comes after system message
            )

            conversationRef.child("messages").push().setValue(welcomeMessage).addOnSuccessListener {
                // Increment unread counter for client
                incrementUnreadCounter(conversationRef.key ?: "", clientId)
                callback()
            }.addOnFailureListener { e ->
                Log.e("SecretaryAppointment", "Error adding welcome message: ${e.message}")
                callback()
            }
        }.addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error adding system message: ${e.message}")
            callback()
        }
    }

    private fun incrementUnreadCounter(conversationId: String, userId: String) {
        val unreadRef = database.child("conversations").child(conversationId)
            .child("unreadMessages").child(userId)

        unreadRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                unreadRef.setValue(currentCount + 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryAppointment", "Error updating unread count: ${error.message}")
            }
        })
    }

    private fun createForwardingNotifications(
        appointment: Appointment,
        originalConversationId: String,
        newConversationId: String,
        problemDescription: String
    ) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val clientId = appointment.clientId
        val lawyerId = appointment.lawyerId

        // Get secretary name
        database.child("secretaries").child(currentSecretaryId).get()
            .addOnSuccessListener { secretarySnapshot ->
                val secretaryName =
                    secretarySnapshot.child("name").getValue(String::class.java) ?: "Secretary"

                // 1. Create notification for client
                createClientForwardingNotification(
                    clientId,
                    appointment,
                    secretaryName,
                    newConversationId,
                    originalConversationId
                )

                // 2. Create notification for lawyer
                createLawyerForwardingNotification(
                    lawyerId,
                    appointment,
                    secretaryName,
                    clientId,
                    newConversationId,
                    problemDescription
                )
            }.addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error fetching secretary name: ${e.message}")

            // Fall back to generic names
            createClientForwardingNotification(
                clientId,
                appointment,
                "a secretary",
                newConversationId,
                originalConversationId
            )

            createLawyerForwardingNotification(
                lawyerId,
                appointment,
                "a secretary",
                clientId,
                newConversationId,
                problemDescription
            )
        }
    }

    private fun createClientForwardingNotification(
        clientId: String,
        appointment: Appointment,
        secretaryName: String,
        newConversationId: String,
        originalConversationId: String
    ) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Create notification for client
        val notificationRef = database.child("notifications").child(clientId).push()
        val notificationId = notificationRef.key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to currentSecretaryId,
            "senderName" to "Secretary $secretaryName",
            "message" to "Your case has been forwarded to Atty. ${appointment.lawyerName} who will assist you further.",
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "CONVERSATION_FORWARDED",
            "isRead" to false,
            "conversationId" to newConversationId,
            "originalConversationId" to originalConversationId,
            "appointmentId" to appointment.appointmentId
        )

        notificationRef.setValue(notificationData).addOnSuccessListener {
            // Update unread notification count
            updateClientUnreadNotificationCount(clientId)
        }.addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error creating client notification: ${e.message}")
        }
    }

    private fun createLawyerForwardingNotification(
        lawyerId: String,
        appointment: Appointment,
        secretaryName: String,
        clientId: String,
        newConversationId: String,
        problemDescription: String
    ) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Format date and time
        val currentDate = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        val currentTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())

        // Create formatted forwarding message
        val forwardingMessage = "The case been forwarded\n" +
                "from secretary $secretaryName regarding\n" +
                "appointment on $currentDate at $currentTime.\n" +
                "Original problem: $problemDescription"

        // Create notification for lawyer
        val notificationRef = database.child("notifications").child(lawyerId).push()
        val notificationId = notificationRef.key ?: return

        val notificationData = mapOf(
            "id" to notificationId,
            "senderId" to currentSecretaryId,
            "senderName" to "Secretary $secretaryName",
            "message" to "A new case from ${appointment.fullName} has been forwarded to you.",
            "forwardingMessage" to forwardingMessage,
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "NEW_CASE_ASSIGNED",
            "isRead" to false,
            "conversationId" to newConversationId,
            "clientId" to clientId,
            "appointmentId" to appointment.appointmentId
        )

        notificationRef.setValue(notificationData).addOnFailureListener { e ->
            Log.e("SecretaryAppointment", "Error creating lawyer notification: ${e.message}")
        }
    }

    private fun updateClientUnreadNotificationCount(clientId: String) {
        // Query to count unread notifications
        database.child("notifications").child(clientId)
            .orderByChild("isRead").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val unreadCount = snapshot.childrenCount.toInt()

                    // Update the client's unread notification count
                    database.child("Users").child(clientId)
                        .child("unreadNotifications").setValue(unreadCount)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        "SecretaryAppointment",
                        "Failed to count unread notifications: ${error.message}"
                    )
                }
            })
    }
}