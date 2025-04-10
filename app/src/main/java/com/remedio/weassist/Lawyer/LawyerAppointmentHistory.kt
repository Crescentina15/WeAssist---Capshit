package com.remedio.weassist.Lawyer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.Models.ConsultationAdapter
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.*

class LawyerAppointmentHistory : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator
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
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        consultationList = ArrayList()
        adapter = ConsultationAdapter(consultationList)
        recyclerView.adapter = adapter

        // Set up swipe to delete
        setupSwipeToDelete()

        // Set up edit functionality
        adapter.onItemEdit = { consultation, newNotes, position ->
            updateConsultationNotes(consultation, newNotes, position)
        }

        // Show loading state initially
        showLoading()

        loadConsultations()

        return view
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
                adapter.deleteItem(position)

                // Show undo snackbar
                Snackbar.make(recyclerView, "Consultation deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        // Undo the deletion by reloading the data from Firebase
                        loadConsultations()
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Handle the actual deletion from Firebase when confirmed
        adapter.onItemDelete = { consultation, position ->
            deleteConsultationFromFirebase(consultation)
        }
    }

    private fun deleteConsultationFromFirebase(consultation: Consultation) {
        database = FirebaseDatabase.getInstance().reference.child("consultations")

        // Find and remove the consultation from Firebase
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
                // Handle error
                Snackbar.make(recyclerView, "Failed to delete consultation", Snackbar.LENGTH_SHORT).show()
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
                            // Update the notes in Firebase
                            consultationSnapshot.ref.child("notes").setValue(newNotes)
                                .addOnSuccessListener {
                                    // Update local list and UI
                                    adapter.updateItem(position, newNotes)
                                    Snackbar.make(recyclerView, "Notes updated successfully", Snackbar.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Snackbar.make(recyclerView, "Failed to update notes", Snackbar.LENGTH_SHORT).show()
                                }
                            return
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Snackbar.make(recyclerView, "Failed to update notes", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        progressIndicator.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showConsultations() {
        progressIndicator.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun loadConsultations() {
        database = FirebaseDatabase.getInstance().reference.child("consultations")
        val currentLawyerId = auth.currentUser?.uid ?: ""

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                consultationList.clear()
                val tempList = ArrayList<Consultation>()

                for (clientSnapshot in snapshot.children) {
                    for (consultation in clientSnapshot.children) {
                        val consultationData = consultation.getValue(Consultation::class.java)
                        if (consultationData != null && consultationData.lawyerId == currentLawyerId) {
                            tempList.add(consultationData)
                        }
                    }
                }

                // Sort consultations by date and time (newest first)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                consultationList.addAll(tempList.sortedWith(compareByDescending { consult ->
                    try {
                        dateFormat.parse("${consult.consultationDate} ${consult.consultationTime}")
                    } catch (e: Exception) {
                        Date(0) // Default to oldest date if parsing fails
                    }
                }))

                // Update UI based on data
                if (consultationList.isEmpty()) {
                    showEmptyState()
                } else {
                    showConsultations()
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // Show empty state in case of error
                showEmptyState()
            }
        })
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