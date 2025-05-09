package com.remedio.weassist.Lawyer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.ClientAdapter
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.Models.ConsultationAdapter
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*

class LawyerAppointmentHistory : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var clientRecyclerView: RecyclerView
    private lateinit var consultationRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var consultationList: ArrayList<Consultation>
    private lateinit var clientList: ArrayList<Pair<String, List<Consultation>>>
    private lateinit var adapter: ConsultationAdapter
    private lateinit var clientAdapter: ClientAdapter
    private var profileSection: View? = null
    private lateinit var auth: FirebaseAuth
    private var isShowingClientList = true
    private lateinit var rootView: View

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is LawyersDashboardActivity) {
            profileSection = context.findViewById(R.id.profile_section)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_client_list, container, false)
        initializeClientListView(rootView)
        return rootView
    }

    private fun initializeClientListView(view: View) {
        auth = FirebaseAuth.getInstance()
        clientRecyclerView = view.findViewById(R.id.recyclerViewClients)
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        clientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        clientList = ArrayList()
        clientAdapter = ClientAdapter(clientList)
        clientRecyclerView.adapter = clientAdapter

        // Initialize consultation list (but don't set up the view yet)
        consultationList = ArrayList()
        adapter = ConsultationAdapter(consultationList)

        // Set up client click listener
        clientAdapter.onItemClick = { consultations ->
            showConsultationsForClient(consultations)
        }

        showLoading()
        loadClients()
    }

    private fun showConsultationsForClient(consultations: List<Consultation>) {
        isShowingClientList = false

        // Inflate the consultation list view
        val consultationView = layoutInflater.inflate(R.layout.fragment_appointment_history, null)
        initializeConsultationListView(consultationView, consultations)

        // Replace the current view
        (rootView as ViewGroup).removeAllViews()
        (rootView as ViewGroup).addView(consultationView)
    }

    private fun initializeConsultationListView(view: View, consultations: List<Consultation>) {
        consultationRecyclerView = view.findViewById(R.id.recyclerViewAppointments)
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        consultationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        consultationList.clear()
        consultationList.addAll(consultations)
        adapter = ConsultationAdapter(consultationList)
        consultationRecyclerView.adapter = adapter

        // Set up swipe to delete
        setupSwipeToDelete()

        // Set up edit functionality
        adapter.onItemEdit = { consultation, newNotes, position ->
            updateConsultationNotes(consultation, newNotes, position)
        }

        // Set up details button click listener
        adapter.onItemDetailsClick = { consultation ->
            showConsultationDetails(consultation)
        }

        if (consultationList.isEmpty()) {
            showEmptyState()
        } else {
            showContent()
        }
    }

    private fun loadClients() {
        database = FirebaseDatabase.getInstance().reference.child("consultations")
        val currentLawyerId = auth.currentUser?.uid ?: ""

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMap = mutableMapOf<String, MutableList<Consultation>>()

                for (clientSnapshot in snapshot.children) {
                    for (consultation in clientSnapshot.children) {
                        val consultationData = consultation.getValue(Consultation::class.java)
                        if (consultationData != null && consultationData.lawyerId == currentLawyerId) {
                            val clientName = consultationData.clientName
                            if (!tempMap.containsKey(clientName)) {
                                tempMap[clientName] = mutableListOf()
                            }
                            tempMap[clientName]?.add(consultationData)
                        }
                    }
                }

                // Convert map to sorted list of pairs
                clientList.clear()
                clientList.addAll(tempMap.toList().sortedBy { it.first })

                if (clientList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                }

                clientAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                showEmptyState()
            }
        })
    }

    private fun showConsultationDetails(consultation: Consultation) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_consultation_details, null)

        dialogView.findViewById<TextView>(R.id.tvClientName).text = "Client: ${consultation.clientName}"
        dialogView.findViewById<TextView>(R.id.tvConsultationDate).text = "Date: ${consultation.consultationDate}"
        dialogView.findViewById<TextView>(R.id.tvConsultationTime).text = "Time: ${consultation.consultationTime}"
        dialogView.findViewById<TextView>(R.id.tvNotes).text = "Notes: ${consultation.notes}"

        AlertDialog.Builder(requireContext())
            .setTitle("Consultation Details")
            .setView(dialogView)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val consultation = consultationList[position]
                consultationList.removeAt(position)
                adapter.notifyItemRemoved(position)

                Snackbar.make(consultationRecyclerView, "Consultation deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        consultationList.add(position, consultation)
                        adapter.notifyItemInserted(position)
                    }
                    .show()

                deleteConsultationFromFirebase(consultation)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(consultationRecyclerView)
    }

    private fun deleteConsultationFromFirebase(consultation: Consultation) {
        database = FirebaseDatabase.getInstance().reference.child("consultations")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (clientSnapshot in snapshot.children) {
                    for (consultationSnapshot in clientSnapshot.children) {
                        val consultationData = consultationSnapshot.getValue(Consultation::class.java)
                        if (consultationData == consultation) {
                            consultationSnapshot.ref.removeValue()
                            break
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Snackbar.make(consultationRecyclerView, "Failed to delete consultation", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateConsultationNotes(consultation: Consultation, newNotes: String, position: Int) {
        database = FirebaseDatabase.getInstance().reference.child("consultations")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (clientSnapshot in snapshot.children) {
                    for (consultationSnapshot in clientSnapshot.children) {
                        val consultationData = consultationSnapshot.getValue(Consultation::class.java)
                        if (consultationData == consultation) {
                            consultationSnapshot.ref.child("notes").setValue(newNotes)
                                .addOnSuccessListener {
                                    consultationList[position].notes = newNotes
                                    adapter.notifyItemChanged(position)
                                    Snackbar.make(consultationRecyclerView, "Notes updated successfully", Snackbar.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Snackbar.make(consultationRecyclerView, "Failed to update notes", Snackbar.LENGTH_SHORT).show()
                                }
                            return
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Snackbar.make(consultationRecyclerView, "Failed to update notes", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        if (isShowingClientList) {
            clientRecyclerView.visibility = View.GONE
        } else {
            consultationRecyclerView.visibility = View.GONE
        }
        emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        progressIndicator.visibility = View.GONE
        if (isShowingClientList) {
            clientRecyclerView.visibility = View.GONE
        } else {
            consultationRecyclerView.visibility = View.GONE
        }
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showContent() {
        progressIndicator.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
        if (isShowingClientList) {
            clientRecyclerView.visibility = View.VISIBLE
        } else {
            consultationRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        profileSection?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        profileSection?.visibility = View.VISIBLE
    }

}