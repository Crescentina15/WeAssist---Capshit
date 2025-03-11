package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.Models.AppointmentAdapter
import com.remedio.weassist.R

class LawyerAppointmentsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appointmentAdapter: AppointmentAdapter
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
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        appointmentList = mutableListOf()

        appointmentAdapter = AppointmentAdapter(
            appointmentList,
            isClickable = true,
            isClientView = false
        ) { appointment ->
            val intent = Intent(requireContext(), ConsultationActivity::class.java).apply {
                putExtra("client_name", appointment.fullName)
                putExtra("consultation_time", appointment.time)
                putExtra("appointment_id", appointment.appointmentId)
            }
            startActivity(intent)
        }

        recyclerView.adapter = appointmentAdapter

        loadAcceptedAppointments()

        return view
    }

    private fun loadAcceptedAppointments() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val lawyerId = currentUser.uid
        databaseRef = FirebaseDatabase.getInstance().reference
            .child("lawyers").child(lawyerId).child("appointments")

        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                appointmentList.clear()
                for (appointmentSnapshot in snapshot.children) {
                    val appointment = appointmentSnapshot.getValue(Appointment::class.java)
                    appointment?.let { appointmentList.add(it) }
                }
                appointmentAdapter.updateAppointments(appointmentList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load appointments", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
