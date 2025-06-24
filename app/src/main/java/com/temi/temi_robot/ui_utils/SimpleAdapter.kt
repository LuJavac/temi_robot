package com.temi.temi_robot.ui_utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.temi.temi_robot.R
import com.temi.temi_robot.dataclasses.PatrolStates
import java.util.Collections

// Class for adapter objects to choose patrol locations order
class SimpleAdapter(private var patrolStates: PatrolStates) :
    RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {
        private val items = patrolStates.getAllLocations()
        private val itemStates = patrolStates.getStates()

    // Item view holder with text view and checkbox
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textViewItem)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }

    // Create a view holder box
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.box_bar, parent, false)
        return ViewHolder(view)
    }

    // Associate data to items on bind
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item

        // Remove previous listener to prevent triggering it when recycled
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = itemStates[item] != false

        // Update state of checkbox
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            itemStates[item] = isChecked
        }
    }

    override fun getItemCount(): Int = items.size

    // Move item from one position to another when dragged
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    // Updating the patrol states after choosing a new order with the adapter
    fun updatePatrolStates(): PatrolStates {
        patrolStates.setLocations(items)
        patrolStates.setStates(itemStates)
        patrolStates.putPatrolLocationsFirst()

        return patrolStates
    }

}