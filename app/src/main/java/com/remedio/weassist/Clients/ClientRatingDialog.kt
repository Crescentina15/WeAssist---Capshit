package com.remedio.weassist.Clients

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R

class ClientRatingDialog : DialogFragment() {
    private lateinit var ratingBar: RatingBar
    private lateinit var commentBox: EditText
    private lateinit var submitButton: Button
    private var lawyerId: String = ""
    private var appointmentId: String = ""

    companion object {
        private const val ARG_LAWYER_ID = "lawyer_id"
        private const val ARG_APPOINTMENT_ID = "appointment_id"

        fun newInstance(lawyerId: String, appointmentId: String): ClientRatingDialog {
            val args = Bundle().apply {
                putString(ARG_LAWYER_ID, lawyerId)
                putString(ARG_APPOINTMENT_ID, appointmentId)
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

            submitButton.setOnClickListener {
                submitRating()
            }

            builder.setView(view)
                .setCancelable(false) // Prevent dismissing by tapping outside

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun submitRating() {
        val rating = ratingBar.rating
        val comment = commentBox.text.toString().trim()

        if (rating == 0f) {
            Toast.makeText(context, "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val clientId = currentUser.uid

        val ratingData = hashMapOf(
            "rating" to rating,
            "comment" to comment,
            "clientId" to clientId,
            "lawyerId" to lawyerId,
            "appointmentId" to appointmentId,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance().reference
            .child("ratings")
            .child(lawyerId)
            .child(appointmentId)
            .setValue(ratingData)
            .addOnSuccessListener {
                Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }
}