package com.remedio.weassist.Clients

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.R
import java.util.UUID

class ClientRatingDialog : DialogFragment() {
    private lateinit var ratingBar: RatingBar
    private lateinit var commentBox: EditText
    private lateinit var submitButton: Button
    private var lawyerId: String = ""
    private var appointmentId: String = ""
    private var clientId: String = ""

    private lateinit var lawyerNameText: TextView
    private lateinit var sessionDateText: TextView

    companion object {
        private const val ARG_LAWYER_ID = "lawyer_id"
        private const val ARG_APPOINTMENT_ID = "appointment_id"
        private const val ARG_CLIENT_ID = "client_id"

        fun newInstance(lawyerId: String, appointmentId: String, clientId: String): ClientRatingDialog {
            val args = Bundle().apply {
                putString(ARG_LAWYER_ID, lawyerId)
                putString(ARG_APPOINTMENT_ID, appointmentId)
                putString(ARG_CLIENT_ID, clientId)
            }
            return ClientRatingDialog().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            lawyerId = it.getString(ARG_LAWYER_ID) ?: ""
            appointmentId = it.getString(ARG_APPOINTMENT_ID) ?: ""
            clientId = it.getString(ARG_CLIENT_ID) ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_client_rating, null)

            ratingBar = view.findViewById(R.id.ratingBar)
            commentBox = view.findViewById(R.id.commentBox)
            submitButton = view.findViewById(R.id.btnSubmit)
            lawyerNameText = view.findViewById(R.id.lawyerNameText)
            sessionDateText = view.findViewById(R.id.sessionDateText)

            // Set up the dialog
            builder.setView(view)
                .setCancelable(false)

            // Load appointment details
            loadAppointmentDetails()

            submitButton.setOnClickListener {
                submitRating()
            }

            builder.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.dialog_background)
                setCanceledOnTouchOutside(false)
                // Add custom title
                setCustomTitle(createCustomTitle())
            }
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun createCustomTitle(): TextView {
        return TextView(requireContext()).apply {
            text = "Rate Your Consultation"
            textSize = 20f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.light_gray))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
    }

    private fun loadAppointmentDetails() {
        FirebaseDatabase.getInstance().reference
            .child("accepted_appointment")
            .child(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val lawyerName = snapshot.child("lawyerName").getValue(String::class.java) ?: "the lawyer"
                        val date = snapshot.child("date").getValue(String::class.java) ?: "your recent session"
                        val time = snapshot.child("time").getValue(String::class.java) ?: ""

                        lawyerNameText.text = "With $lawyerName"
                        sessionDateText.text = "Session on $date at $time"
                    } else {
                        // Fallback if appointment data isn't available
                        lawyerNameText.text = "With your lawyer"
                        sessionDateText.text = "Your recent consultation"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    lawyerNameText.text = "With your lawyer"
                    sessionDateText.text = "Your recent consultation"
                    Log.e("ClientRatingDialog", "Error loading appointment details", error.toException())
                }
            })
    }

    private fun submitRating() {
        val rating = ratingBar.rating
        val comment = commentBox.text.toString().trim()

        if (rating == 0f) {
            Toast.makeText(context, "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate a unique ID for this rating
        val ratingId = UUID.randomUUID().toString()

        val ratingData = hashMapOf(
            "rating" to rating,
            "comment" to comment,
            "clientId" to clientId,
            "lawyerId" to lawyerId,
            "appointmentId" to appointmentId,
            "timestamp" to System.currentTimeMillis()
        )

        // Save rating under lawyer's ratings with a unique ID
        FirebaseDatabase.getInstance().reference
            .child("ratings")
            .child(lawyerId)
            .child(ratingId)  // Use unique ID instead of appointmentId
            .setValue(ratingData)
            .addOnSuccessListener {
                // Remove from pending ratings (if this is the first rating for this appointment)
                FirebaseDatabase.getInstance().reference
                    .child("pending_ratings")
                    .child(clientId)
                    .child(appointmentId)
                    .removeValue()
                    .addOnCompleteListener {
                        Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }
}