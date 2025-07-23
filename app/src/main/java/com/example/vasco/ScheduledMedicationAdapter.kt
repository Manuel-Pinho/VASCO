package com.example.vasco.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.vasco.R
import com.example.vasco.model.ScheduledMedication
import java.text.SimpleDateFormat
import java.util.*

class ScheduledMedicationAdapter(
    private var scheduledMeds: List<ScheduledMedication>,
    private val onItemClick: (ScheduledMedication) -> Unit
) : RecyclerView.Adapter<ScheduledMedicationAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm'h'", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scheduled_medication, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scheduledMed = scheduledMeds[position]
        holder.bind(scheduledMed, timeFormat)
        holder.itemView.setOnClickListener { onItemClick(scheduledMed) }
    }

    override fun getItemCount(): Int = scheduledMeds.size

    fun updateData(newScheduledMeds: List<ScheduledMedication>) {
        this.scheduledMeds = newScheduledMeds.sortedBy { it.scheduledTimestamp } // Ordena por hora
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutRoot: RelativeLayout =
            itemView.findViewById(R.id.itemScheduledMedicationLayout)
        private val tvMedName: TextView = itemView.findViewById(R.id.tvScheduledMedName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvScheduledMedStatus)
        private val tvTime: TextView = itemView.findViewById(R.id.tvScheduledTime)

        fun bind(med: ScheduledMedication, timeFormat: SimpleDateFormat) {
            // usa string formatada
            tvMedName.text = itemView.context.getString(
                R.string.scheduled_med_item_text,
                med.quantidadeATomar,
                med.nomeMedicamento,
                med.dosagemMedicamento ?: ""
            ).trim()

            tvTime.text = timeFormat.format(Date(med.scheduledTimestamp))

            when (med.status.lowercase(Locale.getDefault())) {
                // utiliza strings externalizadas
                itemView.context.getString(R.string.scheduled_status_pending).lowercase() -> {
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = itemView.context.getString(R.string.scheduled_status_pending)
                    tvStatus.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.red_error
                        )
                    )
                    layoutRoot.setBackgroundResource(R.drawable.bg_scheduled_med_pending)
                }

                itemView.context.getString(R.string.scheduled_status_taken).lowercase() -> {
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = itemView.context.getString(R.string.scheduled_status_taken)
                    tvStatus.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.green_success
                        )
                    )
                    layoutRoot.setBackgroundResource(R.drawable.bg_scheduled_med_taken)
                }

                else -> {
                    // caso "ignoradо" ou outros
                    tvStatus.visibility = View.GONE
                    layoutRoot.setBackgroundResource(R.drawable.bg_scheduled_med_normal)
                }
            }
        }
    }
}