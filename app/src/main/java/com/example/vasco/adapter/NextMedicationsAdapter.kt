package com.example.vasco.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vasco.R
import com.example.vasco.model.ScheduledMedication

class NextMedicationsAdapter(
    private var medications: List<ScheduledMedication>,
    private val onItemClick: (ScheduledMedication) -> Unit
) : RecyclerView.Adapter<NextMedicationsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_next_medication, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val med = medications[position]
        holder.bind(med, onItemClick)
    }

    override fun getItemCount(): Int = medications.size

    fun updateData(newMeds: List<ScheduledMedication>) {
        this.medications = newMeds
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivMedImage: ImageView = itemView.findViewById(R.id.ivNextMedImage)
        private val tvMedName: TextView = itemView.findViewById(R.id.tvNextMedName)

        fun bind(med: ScheduledMedication, onItemClick: (ScheduledMedication) -> Unit) {
            tvMedName.text = "${med.nomeMedicamento} ${med.dosagemMedicamento ?: ""}".trim()
            if (!med.imagemUrlMedicamento.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(med.imagemUrlMedicamento)
                    .placeholder(R.drawable.ic_placeholder_med)
                    .error(R.drawable.ic_placeholder_error_med)
                    .into(ivMedImage)
            } else {
                ivMedImage.setImageResource(R.drawable.ic_default_med_icon)
            }
            itemView.setOnClickListener { onItemClick(med) }
        }
    }
} 