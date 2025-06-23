package com.temi.temi_robot

data class TimeSlot (private val hoursStart: String, private val minutesStart: String, private val hoursEnd: String, private val minutesEnd: String){

    fun oneIsBlank(): Boolean{
        return hoursStart.isBlank() || minutesStart.isBlank() || hoursEnd.isBlank() || minutesEnd.isBlank()
    }

    fun isInvalidTime(): Boolean{
        val hourStart = hoursStart.toIntOrNull()
        val minuteStart = minutesStart.toIntOrNull()
        val hourEnd = hoursEnd.toIntOrNull()
        val minuteEnd = minutesEnd.toIntOrNull()

        return hourStart == null || minuteStart == null || hourEnd  == null || minuteEnd == null || hourStart !in 0..23 || minuteStart !in 0..59 || hourEnd !in 0..23 || minuteEnd !in 0..59
    }
}