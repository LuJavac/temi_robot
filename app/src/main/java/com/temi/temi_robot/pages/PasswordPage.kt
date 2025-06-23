package com.temi.temi_robot.pages

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R

// Page to enter password to access the setting page
class PasswordPage : Fragment(){
    private val correctPassword = "nyp123"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        //View layout
        val view = inflater.inflate(R.layout.layout_password, container, false)

        // Page items
        val editTextPassword = view.findViewById<EditText>(R.id.editTextPassword)
        val checkboxShowPassword = view.findViewById<CheckBox>(R.id.checkboxShowPassword)
        val buttonGo = view.findViewById<Button>(R.id.buttonGo)
        val buttonCancel = view.findViewById<Button>(R.id.buttonCancel)

        // Checkbox behavior to show or hide password
        checkboxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editTextPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                editTextPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            editTextPassword.setSelection(editTextPassword.text.length)
        }

        // When confirming the password check if it is correct or not
        buttonGo.setOnClickListener {
            val inputPassword = editTextPassword.text.toString()
            if (inputPassword == correctPassword) {

                // Know where we came from
                val from = arguments?.getString("from")
                if (from == "patrolSettings") {
                    // Change view to settings page
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SettingsPage())
                        .addToBackStack(null)
                        .commit()
                } else {
                    // Change view to time page
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, TimePage())
                        .addToBackStack(null)
                        .commit()
                }
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Access Denied")
                    .setMessage("Incorrect password. Please try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        // When cancelling go back to patrol page
        buttonCancel.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PatrolPage())
                .addToBackStack(null)
                .commit()
        }


        return view
    }
}