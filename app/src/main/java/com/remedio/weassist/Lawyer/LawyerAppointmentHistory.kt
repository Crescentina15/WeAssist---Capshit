package com.remedio.weassist.Lawyer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.Models.ConsultationAdapter
import com.remedio.weassist.R

class LawyerAppointmentHistory : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var consultationList: ArrayList<Consultation>
    private lateinit var adapter: ConsultationAdapter
    private var profileSection: View? = null
    private lateinit var auth: FirebaseAuth

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is LawyersDashboardActivity) {
            profileSection = context.findViewById(R.id.profile_section)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_appointment_history, container, false)

        auth = FirebaseAuth.getInstance()
        recyclerView = view.findViewById(R.id.recyclerViewAppointments)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        consultationList = ArrayList()
        adapter = ConsultationAdapter(consultationList)
        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().reference.child("consultations")

        val currentLawyerId = auth.currentUser?.uid ?: ""

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                consultationList.clear()
                for (clientSnapshot in snapshot.children) {
                    for (consultation in clientSnapshot.children) {
                        val consultationData = consultation.getValue(Consultation::class.java)
                        if (consultationData != null && consultationData.lawyerId == currentLawyerId) {
                            consultationList.add(consultationData)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        return view
    }

    override fun onResume() {
        super.onResume()
        profileSection?.visibility = View.GONE // Hide profile section
    }

    override fun onPause() {
        super.onPause()
        profileSection?.visibility = View.VISIBLE // Show profile section when leaving
    }
}