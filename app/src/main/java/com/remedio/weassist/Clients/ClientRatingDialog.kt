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

    private val database = FirebaseDatabase.getInstance().reference

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

            builder.setView(view)
                .setCancelable(false)

            loadAppointmentDetails()

            submitButton.setOnClickListener {
                submitRating()
            }

            builder.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.dialog_background)
                setCanceledOnTouchOutside(false)
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
        database.child("accepted_appointment")
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

        val ratingId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val ratingData = hashMapOf(
            "rating" to rating,
            "comment" to comment,
            "clientId" to clientId,
            "lawyerId" to lawyerId,
            "appointmentId" to appointmentId,
            "timestamp" to timestamp
        )

        // First save the new rating
        database.child("ratings")
            .child(lawyerId)
            .child(ratingId)
            .setValue(ratingData)
            .addOnSuccessListener {
                // After saving the rating, update the average
                updateLawyerAverageRating(rating)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLawyerAverageRating(newRating: Float) {
        database.child("ratings")
            .child(lawyerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        var totalRatings = 0f
                        var ratingCount = 0

                        // Calculate total ratings and count
                        for (ratingSnapshot in snapshot.children) {
                            val rating = ratingSnapshot.child("rating").getValue(Float::class.java) ?: 0f
                            totalRatings += rating
                            ratingCount++
                        }

                        // Calculate new average and round to 1 decimal place
                        val averageRating = (totalRatings / ratingCount).toBigDecimal()
                            .setScale(1, java.math.RoundingMode.HALF_UP)
                            .toFloat()

                        // Update lawyer's average rating
                        database.child("lawyers")
                            .child(lawyerId)
                            .child("averageRating")
                            .setValue(averageRating)
                            .addOnSuccessListener {
                                // Remove from pending ratings after everything is done
                                removePendingRating()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Rating submitted but failed to update average", Toast.LENGTH_SHORT).show()
                                removePendingRating()
                            }
                    } else {
                        // This shouldn't happen since we just added a rating
                        removePendingRating()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Failed to calculate average rating", Toast.LENGTH_SHORT).show()
                    removePendingRating()
                }
            })
    }
    private fun removePendingRating() {
        database.child("pending_ratings")
            .child(clientId)
            .child(appointmentId)
            .removeValue()
            .addOnCompleteListener {
                Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Feedback submitted but failed to clear pending", Toast.LENGTH_SHORT).show()
                dismiss()
            }
    }
}