package com.temi.temi_robot.time

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.temi.temi_robot.time.TimeListener
import com.temi.temi_robot.dataclasses.TimeSlot
import java.util.Calendar

class AlarmScheduler(private var context: Context){

    private val timeSlotsMaxNumber = 3

    // Schedule a new alarm
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleTimeSlotAlarm(hour: Int, minute: Int, type: String, forTomorrow: Boolean ,requestCode: Int) {

        // Get android alarm planning service
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Converting time slot into Unix calendar unit time
        val startMillis = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            /*
            if(forTomorrow){
                add(Calendar.DATE, 1)
            }*/
            set(Calendar.HOUR_OF_DAY, hour)

            if(forTomorrow){
                add(Calendar.MINUTE, 5)
            }

            //set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Starting TimeListener service even when app not currently executed
        val startIntent = Intent(context, TimeListener::class.java).apply {
            putExtra("type", type)
            putExtra("requestCode", requestCode)
            putExtra("timestamp", startMillis)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            startMillis,
            startPendingIntent
        )

    }

    // Cancel an alarm
    fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, TimeListener::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun setAllAlarms(timeSlots: List<TimeSlot>){
        // Kill all alarms before setting new ones
        for (i in 0 until timeSlotsMaxNumber) {
            cancelAlarm(i)
            cancelAlarm(i + timeSlotsMaxNumber)
        }

        // Plan an alarm for each time slot
        timeSlots.forEachIndexed { index, slot ->
            if(slot.getState()){
                scheduleTimeSlotAlarm(slot.getStartingHour().toInt(), slot.getStartingMinute().toInt(), "start", forTomorrow = false, index)
                scheduleTimeSlotAlarm(slot.getEndingHour().toInt(), slot.getEndingMinute().toInt(), "end", forTomorrow = false, index+timeSlotsMaxNumber) // Offset between start and end alarms to avoid conflicts
            }
        }
    }

    fun getMaxTimeSlotsNumber(): Int {
        return timeSlotsMaxNumber
    }
}