package com.temi.temi_robot.dataclasses

data class TimeSlot (private val hoursStart: String, private val minutesStart: String, private val hoursEnd: String, private val minutesEnd: String, private val isActive: Boolean){

    // Returns if one of the values of the time slots has not been filled by the user
    fun oneIsBlank(): Boolean{
        return hoursStart.isBlank() || minutesStart.isBlank() || hoursEnd.isBlank() || minutesEnd.isBlank()
    }

    // Check if the numbers entered are a time value
    fun isInvalidTime(): Boolean{
        val hourStart = hoursStart.toIntOrNull()
        val minuteStart = minutesStart.toIntOrNull()
        val hourEnd = hoursEnd.toIntOrNull()
        val minuteEnd = minutesEnd.toIntOrNull()

        return hourStart == null || minuteStart == null || hourEnd  == null || minuteEnd == null || hourStart !in 0..23 || minuteStart !in 0..59 || hourEnd !in 0..23 || minuteEnd !in 0..59
    }

    // Convert start to minutes
    fun startInMinutes(): Int? {
        val h = hoursStart.toIntOrNull()
        val m = minutesStart.toIntOrNull()
        return if (h != null && m != null) h * 60 + m else null
    }

    // Convert end to minutes
    fun endInMinutes(): Int? {
        val h = hoursEnd.toIntOrNull()
        val m = minutesEnd.toIntOrNull()
        return if (h != null && m != null) h * 60 + m else null
    }

    // Checks if ending hour is after starting hour
    fun endsAfterStart(): Boolean {
        val start = startInMinutes()
        val end = endInMinutes()
        return if (start != null && end != null) end > start else false
    }

    /// Getters and setters
    fun getStartingHour() : String{
        return hoursStart
    }

    fun getStartingMinute(): String{
        return minutesStart
    }

    fun getEndingHour(): String{
        return hoursEnd
    }

    fun getEndingMinute(): String{
        return minutesEnd
    }

    fun getState(): Boolean{
        return isActive
    }
}