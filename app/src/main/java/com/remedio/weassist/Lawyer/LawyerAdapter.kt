package com.remedio.weassist.Lawyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.remedio.weassist.R

class LawyerAdapter(
    private val lawyersList: List<Lawyer>,
    private val onLawyerClick: (Lawyer) -> Unit,
    private val isTopLawyer: Boolean = false,
    private val onDirectionsClick: ((Lawyer) -> Unit)? = null // Optional parameter for directions
) : RecyclerView.Adapter<LawyerAdapter.LawyerViewHolder>() {

    class LawyerViewHolder(itemView: View, isTopLawyer: Boolean) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.lawyer_name)
        val specializationTextView: TextView = itemView.findViewById(R.id.lawyer_specialization)
        val profileImageView: ImageView = itemView.findViewById(R.id.lawyer_image)
        val ratingsTextView: TextView? = itemView.findViewById(R.id.lawyer_ratings)
        val directionsButton: ImageButton? = if (!isTopLawyer) itemView.findViewById(R.id.directionsButton) else null

        // Only for regular list
        val locationTextView: TextView? = if (!isTopLawyer) itemView.findViewById(R.id.lawyer_location) else null
        val firmTextView: TextView? = if (!isTopLawyer) itemView.findViewById(R.id.lawyer_firm) else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LawyerViewHolder {
        val layoutRes = if (isTopLawyer) R.layout.item_top_lawyer else R.layout.item_lawyer
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return LawyerViewHolder(view, isTopLawyer)
    }

    override fun onBindViewHolder(holder: LawyerViewHolder, position: Int) {
        val lawyer = lawyersList[position]
        holder.nameTextView.text = lawyer.name
        holder.specializationTextView.text = lawyer.specialization

        // Handle directions button only if it exists in the layout
        holder.directionsButton?.let { button ->
            if (onDirectionsClick != null) {
                // Make the button visible and set up click listener
                button.visibility = View.VISIBLE
                button.setOnClickListener {
                    onDirectionsClick.invoke(lawyer)
                }
            } else {
                // If no directions click handler provided, hide the button
                button.visibility = View.GONE
            }
        }

        // Load profile image
        if (!lawyer.profileImageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(lawyer.profileImageUrl)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.profile)
        }

        if (isTopLawyer) {
            // For top lawyers, show just the rating number
            val averageRating = lawyer.averageRating ?: 0.0
            holder.ratingsTextView?.text = "%.1f".format(averageRating)
        } else {
            // For regular list items
            // Show location with distance if available
            if (lawyer.distance != null) {
                val distanceStr = when {
                    lawyer.distance < 1.0 -> "${(lawyer.distance * 1000).toInt()} m"
                    else -> String.format("%.1f km", lawyer.distance)
                }
                holder.locationTextView?.text = "📍 ${lawyer.location.split("[")[0]} ($distanceStr)"
            } else {
                holder.locationTextView?.text = "📍 ${lawyer.location.split("[")[0]}"
            }

            holder.firmTextView?.text = "🏢 ${lawyer.lawFirm}"

            // Show star rating with averageRating if available, otherwise show "Not rated"
            holder.ratingsTextView?.text = when {
                lawyer.averageRating != null -> "⭐ ${"%.1f".format(lawyer.averageRating)}"
                !lawyer.rate.isNullOrEmpty() -> "⭐ ${lawyer.rate}" // Fallback to rate if averageRating is null
                else -> "⭐ Not rated"
            }
        }

        holder.itemView.setOnClickListener {
            if (lawyer.id.isNotEmpty()) {
                onLawyerClick(lawyer)
            } else {
                Toast.makeText(holder.itemView.context, "Lawyer information not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = lawyersList.size
}