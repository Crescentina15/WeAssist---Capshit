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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private lateinit var profileImageView: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_dashboard, container, false)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference("secretaries")

        secretaryNameTextView = view.findViewById(R.id.secretary_fname)
        secretaryFirmTextView = view.findViewById(R.id.secretary_firm)
        profileImageView = view.findViewById(R.id.profile_image) // Initialize the ImageView

        loadSecretaryDetails()

        val manageAvailabilityButton = view.findViewById<ImageButton>(R.id.manage_availability_button)
        val addBackgroundButton = view.findViewById<ImageButton>(R.id.add_background_button)
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

        // Set up RecyclerView for accepted appointments
        recyclerView = view.findViewById(R.id.today_task_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Change this part in your SecretaryDashboardFragment
        appointmentAdapter = SecretaryAppointmentAdapter(appointmentList) { appointment ->
            // Update Firebase
            FirebaseDatabase.getInstance().reference
                .child("lawyers").child(appointment.lawyerId).child("active_sessions")
                .child(appointment.appointmentId)
                .setValue(true)
                .addOnSuccessListener {
                    Toast.makeText(
                        requireContext(),
                        "Session started for ${appointment.fullName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { e ->
                    // Revert UI if Firebase update fails - using the new method name
                    appointmentAdapter.updateSessionState(appointment.appointmentId, false)
                    Toast.makeText(
                        requireContext(),
                        "Failed to start session: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }


        recyclerView.adapter = appointmentAdapter

        fetchAcceptedAppointments()

        return view
    }



    override fun onResume() {
        super.onResume()
        appointmentAdapter.startListeningForSessions()
    }

    override fun onPause() {
        super.onPause()
        appointmentAdapter.stopListeningForSessions()
    }

    private fun setupNotificationBadge(notificationButton: ImageButton) {
        val badge = LayoutInflater.from(context).inflate(R.layout.notification_badge, null)
        val tvBadgeCount = badge.findViewById<TextView>(R.id.tvBadgeCount)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END or Gravity.TOP
        params.setMargins(0, 0, 0, 0)

        val buttonParent = notificationButton.parent as ViewGroup
        val buttonIndex = buttonParent.indexOfChild(notificationButton)

        val frameLayout = FrameLayout(requireContext())

        buttonParent.removeView(notificationButton)
        frameLayout.addView(notificationButton)
        frameLayout.addView(badge, params)
        buttonParent.addView(frameLayout, buttonIndex)

        badge.visibility = View.GONE

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

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

        // Load profile picture
        databaseReference.child(userId).child("profilePicture").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the fragment is still attached and in a valid state
                if (!isAdded || isDetached || view == null) return

                val profilePicUrl = snapshot.value?.toString()
                if (!profilePicUrl.isNullOrEmpty()) {
                    profileImageView?.let { imageView ->
                        Glide.with(requireContext())
                            .load(profilePicUrl)
                            .placeholder(R.drawable.account_circle_24)
                            .error(R.drawable.account_circle_24)
                            .into(imageView)
                    }
                } else {
                    profileImageView?.setImageResource(R.drawable.account_circle_24) // Set default image if URL is null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryDashboardFragment", "Error loading profile picture: ${error.message}")
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

                            lawyerRef.child(appointment.lawyerId).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(lawyerSnapshot: DataSnapshot) {
                                    val lawyerLawFirm = lawyerSnapshot.child("lawFirm").value?.toString()
                                    val lawyerName = lawyerSnapshot.child("name").value?.toString() ?: "Unknown Lawyer"
                                    val lawyerProfileImage = lawyerSnapshot.child("profileImageUrl").value?.toString()

                                    if (lawyerLawFirm == secretaryLawFirm) {
                                        appointment.fullName = lawyerName
                                        appointment.lawyerProfileImage = lawyerProfileImage // Set the lawyer's profile image URL
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


}