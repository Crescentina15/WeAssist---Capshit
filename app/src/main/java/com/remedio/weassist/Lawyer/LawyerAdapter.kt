package com.remedio.weassist.Lawyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.remedio.weassist.R

class LawyerAdapter(
    private val lawyersList: List<Lawyer>,
    private val onLawyerClick: (Lawyer) -> Unit
) : RecyclerView.Adapter<LawyerAdapter.LawyerViewHolder>() {

    class LawyerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.lawyer_name)
        val specializationTextView: TextView = itemView.findViewById(R.id.lawyer_specialization)
        val locationTextView: TextView = itemView.findViewById(R.id.lawyer_location)
        val ratingsTextView: TextView = itemView.findViewById(R.id.lawyer_ratings)
        val firmTextView: TextView = itemView.findViewById(R.id.lawyer_firm)
        val profileImageView: ImageView = itemView.findViewById(R.id.lawyer_image) // Add this line
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LawyerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lawyer, parent, false)
        return LawyerViewHolder(view)
    }

    override fun onBindViewHolder(holder: LawyerViewHolder, position: Int) {
        val lawyer = lawyersList[position]
        holder.nameTextView.text = lawyer.name
        holder.specializationTextView.text = lawyer.specialization
        holder.locationTextView.text = "Location: ${lawyer.location}"
        holder.ratingsTextView.text = "Ratings: ${lawyer.ratings}"
        holder.firmTextView.text = "Law Firm: ${lawyer.lawFirm}"

        // Load profile image using Glide
        if (!lawyer.profileImageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(lawyer.profileImageUrl)
                .placeholder(R.drawable.profile) // Placeholder image
                .error(R.drawable.profile) // Error image in case of failure
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.profile) // Default image if URL is empty
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            onLawyerClick(lawyer)
        }
    }

    override fun getItemCount(): Int = lawyersList.size
}