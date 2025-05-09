package com.remedio.weassist.Models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.Models.Consultation
import com.remedio.weassist.R

class ClientAdapter(private val clientList: List<Pair<String, List<Consultation>>>) :
    RecyclerView.Adapter<ClientAdapter.ClientViewHolder>() {

    var onItemClick: ((List<Consultation>) -> Unit)? = null

    class ClientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clientName: TextView = itemView.findViewById(R.id.tvClientName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_client, parent, false)
        return ClientViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        val (clientName, consultations) = clientList[position]
        holder.clientName.text = clientName
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(consultations)
        }
    }

    override fun getItemCount(): Int = clientList.size
}