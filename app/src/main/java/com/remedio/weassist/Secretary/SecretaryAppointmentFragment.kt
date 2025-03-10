package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_appointment, container, false)

        // Initialize
        database = FirebaseDatabase.getInstance().reference
        appointmentRecyclerView = view.findViewById(R.id.appointment_recyclerview)
        appointmentRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        appointmentList = ArrayList()
        lawyerIdList = ArrayList()

        // Fetch the currently logged-in secretary's UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        val secretaryId = currentUser?.uid

        if (secretaryId != null) {
            Log.d("SecretaryCheck", "Logged in as Secretary: $secretaryId")
            fetchSecretaryLawFirm(secretaryId)
        } else {
            Log.e("SecretaryCheck", "No logged-in secretary found.")
        }

        return view
    }

    private fun fetchSecretaryLawFirm(secretaryId: String) {
        val secretaryRef = database.child("secretaries").child(secretaryId)
        secretaryRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawFirm = snapshot.child("lawFirm").getValue(String::class.java).orEmpty()
                    Log.d("SecretaryCheck", "Secretary's lawFirm: $lawFirm")
                    fetchLawyersForLawFirm(lawFirm)
                } else {
                    Log.e("SecretaryCheck", "Secretary data not found in DB for ID: $secretaryId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryCheck", "Error fetching secretary data: ${error.message}")
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
                    } else {
                        Log.d("SecretaryCheck", "No lawyers found for lawFirm: $lawFirm")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryCheck", "Error fetching lawyers: ${error.message}")
                }
            })
    }

    private fun fetchAppointmentsForLawyers(lawyerIds: List<String>) {
        val appointmentsRef = database.child("appointments")
        val lawyersRef = database.child("lawyers") // Assuming lawyers are stored here
        Log.d("SecretaryCheck", "Fetching appointments for lawyerIds: $lawyerIds")

        // First, fetch all lawyer details to get their names
        lawyersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(lawyersSnapshot: DataSnapshot) {
                // Create a map of lawyerId to lawyerName
                val lawyerNames = mutableMapOf<String, String>()
                for (lawyerSnapshot in lawyersSnapshot.children) {
                    val lawyerId = lawyerSnapshot.key ?: continue
                    val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    lawyerNames[lawyerId] = lawyerName
                }

                // Now fetch appointments
                appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        appointmentList.clear()
                        if (snapshot.exists()) {
                            for (child in snapshot.children) {
                                val appointment = child.getValue(Appointment::class.java)
                                val clientId = child.child("clientId").getValue(String::class.java) ?: "Unknown"

                                if (appointment != null && appointment.lawyerId in lawyerIds) {
                                    appointment.clientId = clientId // Assign clientId

                                    // Here's the important part: set the lawyer name based on the ID
                                    appointment.lawyerName = lawyerNames[appointment.lawyerId] ?: "Unknown Lawyer"

                                    Log.d("SecretaryCheck",
                                        "Adding appointment: ${appointment.fullName}, " +
                                                "lawyerId=${appointment.lawyerId}, " +
                                                "lawyerName=${appointment.lawyerName}, " +
                                                "clientId=${appointment.clientId}")

                                    appointmentList.add(appointment)
                                }
                            }

                            // Initialize the adapter with the correct parameters
                            val adapter = AppointmentAdapter(
                                appointments = appointmentList,
                                isClickable = true,
                                isClientView = false,
                                onItemClickListener = { selectedAppointment ->
                                    showAppointmentDetails(selectedAppointment)
                                }
                            )
                            appointmentRecyclerView.adapter = adapter
                        } else {
                            Log.d("SecretaryCheck", "No appointments found in DB.")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("SecretaryCheck", "Error fetching appointments: ${error.message}")
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryCheck", "Error fetching lawyers: ${error.message}")
            }
        })
    }

    private fun showAppointmentDetails(appointment: Appointment) {
        // Display the appointment details in a dialog
        val isSecretaryView = true // Set this to true for the secretary view
        val dialog = AppointmentDetailsDialog.newInstance(appointment, isSecretaryView)
        dialog.show(
            requireActivity().supportFragmentManager,
            "AppointmentDetailsDialog"
        )
    }
}