package com.temi.temi_robot


import android.media.MediaPlayer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

// Activity class
class MainActivity : AppCompatActivity() {

    internal lateinit var robotController: RobotController
    private lateinit var mediaPlayer: MediaPlayer

    private val mapName = "R4 Block Complete (USE THIS) for BOA1" //level 2 backup
    internal var adapter: SimpleAdapter? = null
    internal var savePatrolStatesFileName = "patrolState.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main_activity)

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

        // Load SettingsPage as default
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoadingPage())
                .commit()
        }
    }

    // Release resources on destroy
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}