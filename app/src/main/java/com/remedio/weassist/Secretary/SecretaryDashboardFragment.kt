package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.SecretaryAppointmentAdapter
import com.remedio.weassist.R

class SecretaryDashboardFragment : Fragment() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var secretaryNameTextView: TextView
    private lateinit var secretaryFirmTextView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var appointmentAdapter: SecretaryAppointmentAdapter
    private var appointmentList = mutableListOf<Appointment>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_dashboard, container, false)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference("secretaries")

        secretaryNameTextView = view.findViewById(R.id.secretary_fname)
        secretaryFirmTextView = view.findViewById(R.id.secretary_firm)

        loadSecretaryDetails()

        val manageAvailabilityButton = view.findViewById<ImageButton>(R.id.manage_availability_button)
        val addBackgroundButton = view.findViewById<ImageButton>(R.id.add_background_button)
        val addBalanceButton = view.findViewById<ImageButton>(R.id.add_balance_button)
        val notificationButton = view.findViewById<ImageButton>(R.id.notification_icon)

        // Setup notification badge
        setupNotificationBadge(notificationButton)

        // Set click listener for notification button
        notificationButton.setOnClickListener {
            val intent = Intent(requireContext(), SecretaryNotificationActivity::class.java)
            startActivity(intent)
        }

        manageAvailabilityButton.setOnClickListener { fetchLawFirmAndOpenLawyersList("manage_availability") }
        addBackgroundButton.setOnClickListener { fetchLawFirmAndOpenLawyersList("add_background") }
        addBalanceButton.setOnClickListener { fetchLawFirmAndOpenLawyersList("add_balance") }

        // Set up RecyclerView for accepted appointments
        recyclerView = view.findViewById(R.id.today_task_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        appointmentAdapter = SecretaryAppointmentAdapter(appointmentList) { appointment, isSessionActive ->
            if (isSessionActive) {
                // Start session
                Toast.makeText(requireContext(), "Session started for ${appointment.fullName}", Toast.LENGTH_SHORT).show()
            } else {
                // End session
                endSession(appointment) // Call the function to end session
            }
        }

        recyclerView.adapter = appointmentAdapter

        fetchAcceptedAppointments()

        return view
    }

    private fun setupNotificationBadge(notificationButton: ImageButton) {
        val badge = LayoutInflater.from(context).inflate(R.layout.notification_badge, null)
        val tvBadgeCount = badge.findViewById<TextView>(R.id.tvBadgeCount)

        // Add the badge to the notification button
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END or Gravity.TOP
        params.setMargins(0, 0, 0, 0)

        // Get the parent of the notification button (should be a FrameLayout or similar)
        val buttonParent = notificationButton.parent as ViewGroup
        val buttonIndex = buttonParent.indexOfChild(notificationButton)

        // Create a new FrameLayout to hold the button and the badge
        val frameLayout = FrameLayout(requireContext())

        // Remove button from its parent
        buttonParent.removeView(notificationButton)

        // Add button to FrameLayout
        frameLayout.addView(notificationButton)

        // Add badge to FrameLayout
        frameLayout.addView(badge, params)

        // Add FrameLayout back to the original parent at the same position
        buttonParent.addView(frameLayout, buttonIndex)

        // Initially hide the badge
        badge.visibility = View.GONE

        // Listen for notification count changes
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Listen to notifications
        val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId)
        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0

                for (notificationSnapshot in snapshot.children) {
                    val isRead = notificationSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                    if (!isRead) {
                        unreadCount++
                    }
                }

                // Update badge visibility and count
                if (unreadCount > 0) {
                    badge.visibility = View.VISIBLE
                    tvBadgeCount.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                } else {
                    badge.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryDashboardFragment", "Error loading notification count: ${error.message}")
            }
        })
    }

    private fun loadSecretaryDetails() {
        val userId = auth.currentUser?.uid ?: return

        databaseReference.child(userId).child("name").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                secretaryNameTextView.text = snapshot.value?.toString() ?: "Secretary"
            }

            override fun onCancelled(error: DatabaseError) {
                secretaryNameTextView.text = "Error loading name"
            }
        })

        databaseReference.child(userId).child("lawFirm").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                secretaryFirmTextView.text = snapshot.value?.toString() ?: "No Law Firm Assigned"
            }

            override fun onCancelled(error: DatabaseError) {
                secretaryFirmTextView.text = "Error loading law firm"
            }
        })
    }

    private fun fetchLawFirmAndOpenLawyersList(action: String) {
        val userId = auth.currentUser?.uid ?: return

        databaseReference.child(userId).child("lawFirm").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawFirm = snapshot.value.toString()
                    val intent = Intent(requireContext(), LawyersListActivity::class.java).apply {
                        putExtra("LAW_FIRM", lawFirm)
                        when (action) {
                            "manage_availability" -> putExtra("FROM_MANAGE_AVAILABILITY", true)
                            "add_background" -> putExtra("FROM_ADD_BACKGROUND", true)
                            "add_balance" -> putExtra("FROM_ADD_BALANCE", true)
                        }
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Law firm not found.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error fetching law firm.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchAcceptedAppointments() {
        val userId = auth.currentUser?.uid ?: return

        databaseReference.child(userId).child("lawFirm").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(secretarySnapshot: DataSnapshot) {
                if (!secretarySnapshot.exists()) {
                    Toast.makeText(requireContext(), "Law firm not found", Toast.LENGTH_SHORT).show()
                    return
                }
                val secretaryLawFirm = secretarySnapshot.value.toString()

                val appointmentsRef = FirebaseDatabase.getInstance().getReference("accepted_appointment")
                appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(appointmentSnapshot: DataSnapshot) {
                        appointmentList.clear()
                        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers")

                        for (appointmentSnap in appointmentSnapshot.children) {
                            val appointment = appointmentSnap.getValue(Appointment::class.java) ?: continue

                            // Store the original client name if needed elsewhere
                            val clientName = appointment.fullName

                            // Fetch lawyer details
                            lawyerRef.child(appointment.lawyerId).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                                    val lawyerLawFirm = lawyerSnapshot.child("lawFirm").value?.toString()
                                    val lawyerName = lawyerSnapshot.child("name").value?.toString() ?: "Unknown Lawyer"

                                    // Only add appointments from the same law firm
                                    if (lawyerLawFirm == secretaryLawFirm) {
                                        // Replace the fullName with the lawyer's name
                                        appointment.fullName = lawyerName

                                        appointmentList.add(appointment)
                                        appointmentAdapter.notifyDataSetChanged()
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(requireContext(), "Failed to load accepted appointments", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error fetching law firm", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun endSession(appointment: Appointment) {
        val database = FirebaseDatabase.getInstance()

        val acceptedAppointmentsRef = database.getReference("accepted_appointment")
        val lawyerAppointmentsRef = database.getReference("lawyers").child(appointment.lawyerId).child("appointments")
        val secretaryAppointmentsRef = database.getReference("secretaries").child(appointment.secretaryId).child("appointments")

        // Remove appointment from accepted_appointment
        acceptedAppointmentsRef.child(appointment.appointmentId).removeValue()
            .addOnSuccessListener {
                // Remove appointment from lawyer's list
                lawyerAppointmentsRef.child(appointment.appointmentId).removeValue()
                    .addOnSuccessListener {
                        // Remove appointment from secretary's list
                        secretaryAppointmentsRef.child(appointment.appointmentId).removeValue()
                            .addOnSuccessListener {
                                appointmentList.remove(appointment)
                                appointmentAdapter.notifyDataSetChanged()
                                Toast.makeText(requireContext(), "Session ended for ${appointment.fullName}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to remove appointment from secretary's list", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to remove appointment from lawyer's list", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to end session", Toast.LENGTH_SHORT).show()
            }
    }
}