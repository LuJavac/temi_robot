package com.temi.temi_robot

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.robotemi.sdk.constants.HardButton


public class SpeechControl : ComponentActivity(), RobotController.RobotReadyCallback {
    private lateinit var robotController: RobotController
    private lateinit var mediaPlayer: MediaPlayer

    private val mapName = "R4 Block Complete (USE THIS) for BOA1" //level 2 backup
    private lateinit var locations: MutableList<String>

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SimpleAdapter

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.choose_path)

        // Initialize Chaquo Python
        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }

        // Initialize Python file module
        val py = Python.getInstance()
        val module = py.getModule("main") // file name without .py

        // Create media player
        mediaPlayer = MediaPlayer.create(this, R.raw.nga)

        // Create robot controller instance
        robotController = RobotController(mapName, module, mediaPlayer)

        // Set Callback to listen to robot ready event
        robotController.setRobotReadyCallback(this)

        // Define patrol path choosing interface view
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Define drag and drop actions
        val itemTouchHelper = createDragAndDrop()
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Confirm button to get chosen path locations order and start patrolling
        val confirmButton = findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            val patrolLocations = adapter.getItems()
            setContentView(R.layout.activity_main)

            // Items of next interface
            val nypLogo = findViewById<ImageView>(R.id.my_gif)
            val startButton = findViewById<Button>(R.id.start_button)

            // User button behavior
            startButton.setOnClickListener{
                robotController.setDetectionModeOn(false, 0.5f)
                robotController.setLastRequestTimeNow()
                robotController.stopMovement()
                robotController.resetInactivityTimer()
                robotController.askQuestion("Hi, how can I help you ?")
            }

            // Robot behavior at initialization
            initBehavior()
        }
    }

    override fun onRobotIsReady() {
        if(robotController.askRequiredPermissions()){
            if(robotController.getLocations().isEmpty()){
                robotController.setBlockMode(true)
                robotController.speak("I couldn't find the map or it has no locations. Please check the map name or add locations to your map.")
            }
            else {
                locations = robotController.getLocations().toMutableList()
                adapter = SimpleAdapter(locations)
                recyclerView.adapter = adapter
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    fun initBehavior(){
        robotController.setDetectionModeOn(true, 0.5f)
        robotController.patrol(robotController.getLocations())
        robotController.hideTopBar()
        robotController.setVolume(4)
        robotController.toggleWakeup(true)
        robotController.setTopBadgeEnabled(false)
        robotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED) // CHANGE TO DISABLED
        robotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
    }

    fun createDragAndDrop() : ItemTouchHelper{
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }
        })
        return itemTouchHelper
    }

}
