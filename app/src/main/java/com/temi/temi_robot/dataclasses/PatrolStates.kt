package com.temi.temi_robot.dataclasses

import kotlinx.serialization.Serializable

@Serializable
// Class for saving patrol states
data class PatrolStates(private var locations: List<String>, private var states: MutableMap<String, Boolean>){

    // Returns all locations registered
    fun getAllLocations(): List<String> {
        return locations
    }

    // Setting new locations
    fun setLocations(locations: List<String>) {
        this.locations = locations
    }

    // Returns only the patrol locations
    fun getPatrolLocations(): List<String> {
        return locations.filter { states[it] == true}
    }

    // Re-orders the locations so that the patrol locations are always printed first
    fun putPatrolLocationsFirst(){
        val active = locations.filter { states[it] == true }
        val inactive = locations.filter { states[it] != true }
        locations = active + inactive

        reorderStates()
    }

    // Sets the first patrol location to the end of the patrol locations block
    fun appendFirstPatrolLocation() {
        val firstActive = locations.firstOrNull { states[it] == true } ?: return
        val mutableLocations = locations.toMutableList()

        if (mutableLocations.remove(firstActive)) {
            val insertIndex = mutableLocations.indexOfFirst { states[it] != true }
                .takeIf { it != -1 } ?: mutableLocations.size
            mutableLocations.add(insertIndex, firstActive)
            locations = mutableLocations.toList()

            reorderStates()
        }
    }

    // Getter for states of locations
    fun getStates(): MutableMap<String, Boolean> {
        return states
    }

    // Setter for states of locations
    fun setStates(states: MutableMap<String, Boolean>) {
        this.states = states
    }

    // Reorder states map to match current locations order
    private fun reorderStates() {
        val newStates = LinkedHashMap<String, Boolean>()
        for (key in locations) {
            val value = states[key] != false
            newStates[key] = value
        }
        states = newStates
    }

}