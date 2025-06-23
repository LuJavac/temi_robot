package com.temi.temi_robot.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R
import com.temi.temi_robot.TimeSlot

class TimePage : Fragment() {

    private var timeslotCounter = 0
    private val timeSlotsMaxNumber = 3

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_time, container, false)
        return view

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val editHoursStart = view.findViewById<EditText>(R.id.editTextHoursStart)
        val editMinutesStart = view.findViewById<EditText>(R.id.editTextMinutesStart)
        val editHoursEnd = view.findViewById<EditText>(R.id.editTextHoursEnd)
        val editMinutesEnd = view.findViewById<EditText>(R.id.editTextMinutesEnd)

        val addSlotButton = view.findViewById<Button>(R.id.btnAddSlot)
        val submitButton = view.findViewById<Button>(R.id.btnSubmit)

        addSlotButton.setOnClickListener {
            if(timeslotCounter < timeSlotsMaxNumber){
                addTimeSlotView()
            } else {
                Toast.makeText(requireContext(), "Max number of time slots reached", Toast.LENGTH_SHORT).show()
            }
        }

        submitButton.setOnClickListener {
            val hoursStart = editHoursStart.text.toString()
            val minutesStart = editMinutesStart.text.toString()
            val hoursEnd = editHoursEnd.text.toString()
            val minutesEnd = editMinutesEnd.text.toString()
            val timeSlot = TimeSlot(hoursStart, minutesStart, hoursEnd, minutesEnd)

            if (wrongValues(timeSlot)) {
                return@setOnClickListener
            }

            // Add it to robot
        }
    }

    private fun addTimeSlotView(){
        timeslotCounter++
        val inflater = LayoutInflater.from(requireContext())
        val container = view?.findViewById<LinearLayout>(R.id.slotsContainer)

        val slotView = inflater.inflate(R.layout.time_slot, container, false)
        container?.addView(slotView, container.childCount - 1)


    }

    private fun wrongValues(timeSlot: TimeSlot): Boolean {

        if (timeSlot.oneIsBlank()) {
            Toast.makeText(requireContext(), "Please fill hours and minutes", Toast.LENGTH_SHORT).show()
            return true
        }
        if (timeSlot.isInvalidTime()) {
            Toast.makeText(requireContext(), "Invalid time", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }
}