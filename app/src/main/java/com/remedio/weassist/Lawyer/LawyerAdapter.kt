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
    private val onLawyerClick: (Lawyer) -> Unit,
    private val isTopLawyer: Boolean = false
) : RecyclerView.Adapter<LawyerAdapter.LawyerViewHolder>() {

    class LawyerViewHolder(itemView: View, isTopLawyer: Boolean) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.lawyer_name)
        val specializationTextView: TextView = itemView.findViewById(R.id.lawyer_specialization)
        val profileImageView: ImageView = itemView.findViewById(R.id.lawyer_image)

        // Only for top lawyers
        val ratingsTextView: TextView? = if (isTopLawyer) itemView.findViewById(R.id.lawyer_ratings) else null

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

        if (isTopLawyer) {
            holder.ratingsTextView?.text = "500" // Show perfect rating
        } else {
            holder.locationTextView?.text = "Location: ${lawyer.location}"
            holder.firmTextView?.text = "Law Firm: ${lawyer.lawFirm}"
            // Show actual rate if available
            holder.ratingsTextView?.text = "Rate: ${lawyer.rate ?: "Not rated"}"
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

        holder.itemView.setOnClickListener {
            onLawyerClick(lawyer)
        }
    }

    override fun getItemCount(): Int = lawyersList.size
}