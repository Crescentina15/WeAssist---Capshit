package com.remedio.weassist.Secretary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.remedio.weassist.R
import com.squareup.picasso.Picasso  // For loading images

class SecretaryAppointmentFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var appointmentList: ArrayList<Appointment>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_secretary_appointment, container, false)

        // Initialize RecyclerView and Database reference
        appointmentRecyclerView = view.findViewById(R.id.appointment_recyclerview)  // Make sure to add RecyclerView in XML
        appointmentRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        appointmentList = ArrayList()

        database = FirebaseDatabase.getInstance().reference
        val lawyerId = "9VCxncka5ISREj9tKyfwNCx1UXL2"  // Example lawyerId, use dynamic values as needed

        // Fetch appointments for the specific lawyer
        getAppointmentsForLawyer(lawyerId)

        return view
    }

    private fun getAppointmentsForLawyer(lawyerId: String) {
        val appointmentsRef = database.child("appointments")

        appointmentsRef.orderByChild("lawyerId").equalTo(lawyerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (appointmentSnapshot in snapshot.children) {
                        val appointment = appointmentSnapshot.getValue(Appointment::class.java)
                        appointment?.let {
                            appointmentList.add(it)
                        }
                    }

                    // Once data is fetched, update RecyclerView
                    val adapter = AppointmentAdapter(appointmentList)
                    appointmentRecyclerView.adapter = adapter
                } else {
                    Log.d("Appointment", "No appointments found for this lawyer.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Appointment", "Error fetching appointments: ${error.message}")
            }
        })
    }

    // AppointmentAdapter to bind data to RecyclerView
    inner class AppointmentAdapter(private val appointments: List<Appointment>) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
            // Inflate the layout for each item (item_appointment.xml)
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
            return AppointmentViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
            // Get the appointment at the given position
            val appointment = appointments[position]

            // Bind the data to the views
            holder.appointmentTitle.text = "Appointment with ${appointment.fullName}"
            holder.appointmentDate.text = appointment.date
            holder.appointmentTime.text = appointment.time

            // Load the lawyer's profile image (if any) using Picasso, otherwise set a default image
            if (appointment.lawyerProfileImage != null) {
                Picasso.get().load(appointment.lawyerProfileImage).into(holder.lawyerProfileImage)
            } else {
                holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)  // Default image
            }
        }

        override fun getItemCount(): Int = appointments.size

        // ViewHolder class to hold references to the views
        inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
            val appointmentTitle: TextView = itemView.findViewById(R.id.appointment_title)
            val appointmentDate: TextView = itemView.findViewById(R.id.appointment_date)
            val appointmentTime: TextView = itemView.findViewById(R.id.appointment_time)
        }
    }

}
