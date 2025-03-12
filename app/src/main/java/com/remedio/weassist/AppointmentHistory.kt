package com.remedio.weassist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.Models.ConsultationAdapter


class AppointmentHistory : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var consultationList: ArrayList<Consultation>
    private lateinit var adapter: ConsultationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_appointment_history, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewAppointments)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        consultationList = ArrayList()
        adapter = ConsultationAdapter(consultationList)
        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().reference.child("consultations")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                consultationList.clear()
                for (clientSnapshot in snapshot.children) {
                    for (consultation in clientSnapshot.children) {
                        val consultationData = consultation.getValue(Consultation::class.java)
                        if (consultationData != null) {
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
}
