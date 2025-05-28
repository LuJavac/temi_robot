package com.temi.temi_robot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.robotemi.sdk.constants.HardButton

// Settings page class
public class SettingsPage : Fragment(), RobotController.RobotReadyCallback {

    private lateinit var robotController: RobotController
    private lateinit var locations: MutableList<String>

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SimpleAdapter

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_settings, container, false)

        // Hide top bar for choosing patrol locations
        robotController.hideTopBar()

        // Set Callback to listen to robot ready event
        robotController.setRobotReadyCallback(this)

        // Nyp logo on settings interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Define patrol path choosing interface view
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        if((activity as MainActivity).adapter != null){
            adapter = (activity as MainActivity).adapter!!
            recyclerView.adapter = adapter
        }

        // Define drag and drop actions
        val itemTouchHelper = createDragAndDrop()
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Confirm button to get chosen path locations order and start patrolling
        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            // Getting locations from adapter and setting them in robot controller
            val patrolLocations = adapter.getItems()
            if(patrolLocations.size < 3){
                robotController.setBlockMode(true)
                robotController.speak("Please select at least 3 locations to start patrolling")
                return@setOnClickListener
            }
            robotController.setLocations(patrolLocations)

            // Robot behavior at initialization
            initBehavior()

            // Change view to patrol page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PatrolPage())
                .addToBackStack(null)
                .commit()
        }
        return view
    }

    // When robot is initialized, ask permissions and load map
    override fun onRobotIsReady() {
        if(robotController.askRequiredPermissions()){
            robotController.setBlockMode(true)
            if(robotController.getLocations().isEmpty()){
                robotController.speak("I couldn't find the map or it has no locations. Please check the map name or add locations to your map.")
            }
            else {
                locations = robotController.getLocations().filter{it.lowercase() != "home base"}.toMutableList()
                adapter = SimpleAdapter(locations)
                recyclerView.adapter = adapter
                (activity as MainActivity).adapter = adapter
            }

        }
    }

    // Robot behavior on start
    fun initBehavior(){
        robotController.setBlockMode(false)
        robotController.patrol(robotController.getLocations())
        robotController.hideTopBar()
        robotController.setVolume(4)
        robotController.toggleWakeup(true)
        robotController.setTopBadgeEnabled(false)
        robotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED) // CHANGE TO DISABLED
        robotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
    }

    // Create a drag and drop manager
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
