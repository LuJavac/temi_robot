package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.face.FaceCamera
import com.temi.temi_robot.face.FaceClient
import com.temi.temi_robot.telemetry.TelemetryClient

/**
 * Écran d'enrôlement facial : aperçu caméra live (miroir) + guidage multi-angles.
 * La personne tape son prénom, puis le robot la guide ("regarde-moi", "tourne à
 * gauche/droite…") en prenant une photo nette à chaque étape, et envoie le tout
 * au service face du Pi.
 *
 * Contention caméra : on coupe la détection native Temi à l'entrée pour libérer
 * la caméra frontale ; en quittant, MainPage relance son wave detector.
 */
class EnrollPage : Fragment() {

    private var faceCamera: FaceCamera? = null
    private val handler = Handler(Looper.getMainLooper())
    private val frames = mutableListOf<ByteArray>()

    // (phrase TTS, texte à l'écran) pour chaque angle
    private val captureSteps = listOf(
        "Look straight at me." to "Look straight at me 🙂",
        "Now turn slightly to your left." to "Turn to YOUR left ⬅️",
        "Now turn slightly to your right." to "Turn to YOUR right ➡️",
        "Now lift your chin a little." to "Chin up a little ⬆️",
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.layout_enroll_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Libère la caméra frontale (sinon CameraX échoue en silence)
        RobotController.setDetectionModeOn(false, 0.5f)

        val previewView = view.findViewById<PreviewView>(R.id.previewView)
        val namePanel = view.findViewById<View>(R.id.namePanel)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val guidanceText = view.findViewById<TextView>(R.id.guidanceText)

        faceCamera = FaceCamera(requireContext(), viewLifecycleOwner).also { cam ->
            cam.start(previewView.surfaceProvider) { ok ->
                if (!ok && isAdded) {
                    RobotController.speak("Sorry, I can't access my camera right now.")
                    goBack()
                }
            }
        }

        view.findViewById<Button>(R.id.cancelButton).setOnClickListener { goBack() }

        view.findViewById<Button>(R.id.startButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                RobotController.speak("Please type your name first.")
                return@setOnClickListener
            }
            hideKeyboard(nameInput)
            namePanel.visibility = View.GONE
            captureStep(name, 0, guidanceText)
        }

        RobotController.speak("Let's get to know each other! Please type your name, then press Start.")
    }

    /** Guide la personne angle par angle, en capturant une photo à chaque étape. */
    private fun captureStep(name: String, index: Int, guidanceText: TextView) {
        if (!isAdded) return
        if (index >= captureSteps.size) {
            finishEnroll(name, guidanceText)
            return
        }
        val (speech, label) = captureSteps[index]
        guidanceText.text = label
        RobotController.speak(speech)
        // Laisse le temps d'entendre la consigne et de bouger avant la capture.
        handler.postDelayed({
            faceCamera?.captureJpeg { jpeg ->
                if (jpeg != null) frames.add(jpeg)
                captureStep(name, index + 1, guidanceText)
            }
        }, 2500)
    }

    private fun finishEnroll(name: String, guidanceText: TextView) {
        guidanceText.text = "Saving…"
        if (frames.isEmpty()) {
            RobotController.speak("Sorry, I couldn't capture you well. Let's try again later.")
            goBack()
            return
        }
        FaceClient.enroll(name, frames) { res ->
            if (!isAdded) return@enroll
            if (res.success && res.enrolledPhotos >= 1) {
                RobotController.speak("Nice to meet you, $name! Next time I'll greet you by name.")
                TelemetryClient.track("greeting")
            } else {
                RobotController.speak("Sorry $name, I couldn't save your face this time.")
            }
            goBack()
        }
    }

    private fun goBack() {
        if (isAdded) parentFragmentManager.popBackStack()
    }

    private fun hideKeyboard(focused: View) {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(focused.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        faceCamera?.stop()
        faceCamera = null
    }
}
