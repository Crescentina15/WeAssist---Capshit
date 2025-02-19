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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R
import com.squareup.picasso.Picasso

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

        // 1. Fetch the currently logged-in secretary's UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        val secretaryId = currentUser?.uid

        if (secretaryId != null) {
            Log.d("SecretaryCheck", "Logged in as Secretary: $secretaryId")
            // 2. Fetch the Secretary's Law Firm
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

                    // 3. Fetch Lawyers for this law firm
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
                            // Each child key is the lawyer's UID
                            val lawyerKey = child.key
                            if (!lawyerKey.isNullOrEmpty()) {
                                lawyerIdList.add(lawyerKey)
                                Log.d("SecretaryCheck", "Found lawyer with ID: $lawyerKey")
                            }
                        }
                        // 4. Now fetch appointments for these lawyer IDs
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
        Log.d("SecretaryCheck", "Fetching appointments for lawyerIds: $lawyerIds")

        // 5. We'll read all appointments once and filter them in code
        // (Alternatively, you can do multiple queries if you prefer)
        appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                appointmentList.clear()  // Clear any old data
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val appointment = child.getValue(Appointment::class.java)
                        if (appointment != null) {
                            // Add the appointment only if lawyerId is in our lawyerIds
                            if (appointment.lawyerId in lawyerIds) {
                                Log.d(
                                    "SecretaryCheck",
                                    "Adding appointment: ${appointment.fullName}, lawyerId=${appointment.lawyerId}"
                                )
                                appointmentList.add(appointment)
                            }
                        }
                    }
                    // 6. Display them in RecyclerView
                    val adapter = AppointmentAdapter(appointmentList)
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

    // ---------------------------------------------
    // RecyclerView Adapter
    // ---------------------------------------------
    inner class AppointmentAdapter(private val appointments: List<Appointment>) :
        RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_appointment, parent, false)
            return AppointmentViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
            val appointment = appointments[position]

            // Bind data
            holder.appointmentTitle.text = "Appointment with ${appointment.fullName}"
            holder.appointmentDate.text = appointment.date
            holder.appointmentTime.text = appointment.time

            // If there's a lawyerProfileImage, load with Picasso
            if (!appointment.lawyerProfileImage.isNullOrEmpty()) {
                Picasso.get().load(appointment.lawyerProfileImage).into(holder.lawyerProfileImage)
            } else {
                holder.lawyerProfileImage.setImageResource(R.drawable.account_circle_24)
            }

            // Set click listener
            holder.itemView.setOnClickListener {
                val dialog = AppointmentDetailsDialog.newInstance(appointment)
                dialog.show(
                    (holder.itemView.context as androidx.fragment.app.FragmentActivity).supportFragmentManager,
                    "AppointmentDetailsDialog"
                )
            }
        }

        override fun getItemCount(): Int {
            return appointments.size
        }

        inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lawyerProfileImage: ImageView = itemView.findViewById(R.id.lawyer_profile_image)
            val appointmentTitle: TextView = itemView.findViewById(R.id.appointment_title)
            val appointmentDate: TextView = itemView.findViewById(R.id.appointment_date)
            val appointmentTime: TextView = itemView.findViewById(R.id.appointment_time)
        }
    }
}
