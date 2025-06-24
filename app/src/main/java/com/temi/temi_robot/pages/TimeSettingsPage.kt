package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.temi.temi_robot.AlarmScheduler
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.dataclasses.TimeSlot

class TimeSettingsPage : Fragment() {

    private var timeslotCounter = 0
    private val timeSlotsMaxNumber = 3 // Max time slots number
    private lateinit var alarmScheduler: AlarmScheduler

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        alarmScheduler = AlarmScheduler(context)
    }

    // Creates main view
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_time, container, false)
        return view

    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Defining buttons
        val addSlotButton = view.findViewById<Button>(R.id.btnAddSlot)
        val submitButton = view.findViewById<Button>(R.id.btnSubmit)

        // Adding a new time slot to view
        addSlotButton.setOnClickListener {
            addTimeSlotView()
        }

        submitButton.setOnClickListener {
            val container = view.findViewById<LinearLayout>(R.id.slotsContainer)
            val timeSlots = mutableListOf<TimeSlot>()

            // Checking if each time slots values are correct
            for (i in 0 until container.childCount) {
                val slotView = container.getChildAt(i)

                val hoursStart = slotView.findViewById<EditText>(R.id.editTextHoursStart).text.toString()
                val minutesStart = slotView.findViewById<EditText>(R.id.editTextMinutesStart).text.toString()
                val hoursEnd = slotView.findViewById<EditText>(R.id.editTextHoursEnd).text.toString()
                val minutesEnd = slotView.findViewById<EditText>(R.id.editTextMinutesEnd).text.toString()
                val isActive = slotView.findViewById<CheckBox>(R.id.checkBoxActive).isChecked

                val timeSlot = TimeSlot(hoursStart, minutesStart, hoursEnd, minutesEnd, isActive)

                if (wrongValues(timeSlot)) {
                    return@setOnClickListener
                }

                timeSlots.add(timeSlot)
            }

            // Checking potential overlaps in the time slots to avoid conflicts
            if (hasOverlap(timeSlots)) {
                addAlertMessage("Please don't make a time slot start during another one")
                return@setOnClickListener
            }

            // Plan an alarm for each time slot
            timeSlots.forEachIndexed  { index, slot ->
                if(slot.getState()){
                    alarmScheduler.scheduleTimeSlotAlarm(slot, index)
                }
            }

            // Change view to patrol page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PatrolPage())
                .addToBackStack(null)
                .commit()

        }
    }

    // Adds a new time slot to the view
    private fun addTimeSlotView(){

        timeslotCounter++

        // Adding new time slot view
        val container = view?.findViewById<LinearLayout>(R.id.slotsContainer)
        val inflater = LayoutInflater.from(requireContext())
        inflater.inflate(R.layout.time_slot, container, true)

        // Get the view just added
        val addedView = container?.getChildAt(container.childCount - 1)

        // Setup delete button
        val removeButton = addedView?.findViewById<ImageButton>(R.id.btnRemoveSlot)
        removeButton?.setOnClickListener {
            container.removeView(addedView)
            timeslotCounter--

            // Re-enable "Add" button if under limit
            view?.findViewById<Button>(R.id.btnAddSlot)?.visibility = View.VISIBLE
        }

        // Disable add button if over limit
        if (timeslotCounter >= timeSlotsMaxNumber) {
            view?.findViewById<Button>(R.id.btnAddSlot)?.visibility = View.GONE
        }
    }

    // Test if the time slot is valid
    private fun wrongValues(timeSlot: TimeSlot): Boolean {
        if (timeSlot.oneIsBlank()) {
            addAlertMessage("Please fill the time slots")
            return true
        }
        if (timeSlot.isInvalidTime()) {
            addAlertMessage("Time makes no sense")
            return true
        }
        if (!timeSlot.endsAfterStart()) {
            addAlertMessage("Ending time must be after starting time")
            return true
        }
        return false
    }

    // Defines in time slots are overlapping, to avoid conflicts
    private fun hasOverlap(timeSlots: List<TimeSlot>): Boolean {
        for (i in timeSlots.indices) {
            val aStart = timeSlots[i].startInMinutes() ?: return true
            val aEnd = timeSlots[i].endInMinutes() ?: return true

            for (j in i + 1 until timeSlots.size) {
                val bStart = timeSlots[j].startInMinutes() ?: return true
                val bEnd = timeSlots[j].endInMinutes() ?: return true

                val overlap = !(aEnd <= bStart || bEnd <= aStart)
                if (overlap) return true
            }
        }
        return false
    }

    // To display a message in alert box
    private fun addAlertMessage(message : String){
        AlertDialog.Builder(requireContext())
            .setTitle("Invalid data")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}