package com.remedio.weassist.Lawyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class LawyerAdapter(private val lawyersList: List<Lawyer>) : RecyclerView.Adapter<LawyerAdapter.LawyerViewHolder>() {

    class LawyerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.lawyer_name)
        val specializationTextView: TextView = itemView.findViewById(R.id.lawyer_specialization)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LawyerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lawyer, parent, false)
        return LawyerViewHolder(view)
    }

    override fun onBindViewHolder(holder: LawyerViewHolder, position: Int) {
        val lawyer = lawyersList[position]
        holder.nameTextView.text = lawyer.name
        holder.specializationTextView.text = lawyer.specialization
    }

    override fun getItemCount(): Int = lawyersList.size
}
