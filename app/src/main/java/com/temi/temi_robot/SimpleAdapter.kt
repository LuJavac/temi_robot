package com.temi.temi_robot

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.Serializable
import java.util.Collections

// Class for saving patrol states
@Serializable
data class PatrolState(
    val items: List<String>, // Order
    val itemStates: Map<String, Boolean> // State of checkboxes
)

// Class for adapter objects to choose patrol locations order
class SimpleAdapter(private val items: MutableList<String>) :
    RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {

    // To track whether each item is included in patrol
    private val itemStates = mutableMapOf<String, Boolean>().apply {
        items.forEach { put(it, true) }
    }

    // Item view holder with text view and checkbox
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textViewItem)
        val checkBox: android.widget.CheckBox = view.findViewById(R.id.checkBox)
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

    // Put saved states into itemStates
    @SuppressLint("NotifyDataSetChanged")
    fun restoreStates(states: Map<String, Boolean>) {
        itemStates.clear()
        itemStates.putAll(states)
        notifyDataSetChanged()
    }


    // Only return selected (checked) items
    fun getItems(): List<String> {
        return items.filter { itemStates[it] == true}
    }

    // If needed, get all items regardless of check state
    fun getAllItems(): List<String> = items.toList()

    // Convert to patrol state
    fun toPatrolState(): PatrolState {
        return PatrolState(getAllItems(), itemStates.toMap())
    }
}
