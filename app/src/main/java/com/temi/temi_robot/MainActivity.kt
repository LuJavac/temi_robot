package com.temi.temi_robot


import android.media.MediaPlayer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Activity class
class MainActivity : AppCompatActivity() {
    private val mapName = "R4 Block Complete (USE THIS) for BOA1" //level 2 backup

    internal var robotController: RobotController = RobotController(mapName)
    internal var adapter: SimpleAdapter? = null
    internal var savePatrolStatesFileName = "patrolState.json"
    internal var userRequest : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main_activity)

        // Load SettingsPage as default
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FirstPage())
                .commit()
        }
    }

    // Release resources on destroy
    override fun onDestroy() {
        super.onDestroy()
    }
}