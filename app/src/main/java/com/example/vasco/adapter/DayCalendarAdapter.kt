package com.example.vasco.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vasco.R

class DayCalendarAdapter(
    private val days: List<DayItem>,
    private val onDayClick: (DayItem) -> Unit
) : RecyclerView.Adapter<DayCalendarAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayLetter: TextView = view.findViewById(R.id.tvDayLetter)
        val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_calendar, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        holder.tvDayLetter.text = day.dayLetter
        holder.tvDayNumber.text = day.dayNumber

        // Apply state-based styling
        when (day.state) {
            DayState.SELECTED_RED -> {
                holder.itemView.setBackgroundResource(R.drawable.bg_day_circle_selected_red)
                holder.itemView.scaleX = 1.2f
                holder.itemView.scaleY = 1.2f
            }
            DayState.SELECTED_GREEN -> {
                holder.itemView.setBackgroundResource(R.drawable.bg_day_circle_selected_green)
                holder.itemView.scaleX = 1.0f
                holder.itemView.scaleY = 1.0f
            }
            DayState.NORMAL -> {
                holder.itemView.setBackgroundResource(R.drawable.bg_day_circle_normal)
                holder.itemView.scaleX = 1.0f
                holder.itemView.scaleY = 1.0f
            }
        }

        holder.itemView.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount() = days.size
} 