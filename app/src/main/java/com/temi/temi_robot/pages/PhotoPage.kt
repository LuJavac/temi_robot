package com.temi.temi_robot.pages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.telemetry.TelemetryClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoPage : Fragment() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_photo_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        RobotController.hideTopBar()
        cameraExecutor = Executors.newSingleThreadExecutor()

        val btnCapture = requireView().findViewById<Button>(R.id.btnCapturePhoto)
        val btnCancel = requireView().findViewById<Button>(R.id.btnCancelPhoto)
        val btnCloseQr = requireView().findViewById<Button>(R.id.btnCloseQr)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            RobotController.speak("I need camera permission to take a photo!")
        }

        btnCapture.setOnClickListener {
            btnCapture.isEnabled = false
            startCountdownAndTakePhoto()
        }

        btnCancel.setOnClickListener {
            goBackToMainPage()
        }

        btnCloseQr.setOnClickListener {
            requireView().findViewById<View>(R.id.qrCodeOverlay).visibility = View.GONE
            goBackToMainPage()
        }
    }

    private fun startCountdownAndTakePhoto() {
        val countdownText = requireView().findViewById<TextView>(R.id.countdownText)
        countdownText.visibility = View.VISIBLE

        RobotController.speak("3")
        countdownText.text = "3"

        handler.postDelayed({
            RobotController.speak("2")
            countdownText.text = "2"
        }, 1000)

        handler.postDelayed({
            RobotController.speak("1")
            countdownText.text = "1"
        }, 2000)

        handler.postDelayed({
            countdownText.text = "Smile!"
            RobotController.speak("Smile!")
        }, 3000)

        handler.postDelayed({
            countdownText.visibility = View.GONE
            takePhoto()
        }, 3500)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(requireContext().externalCacheDir, "temi_photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("PhotoPage", "Photo capture failed: ${exc.message}", exc)
                    RobotController.speak("Oops, something went wrong.")
                    requireView().findViewById<Button>(R.id.btnCapturePhoto).isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    RobotController.speak("Great picture! Uploading to server.")
                    TelemetryClient.track("photo_taken")
                    sendPhotoToServer(photoFile)
                }
            }
        )
    }

    private fun sendPhotoToServer(photoFile: File) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", photoFile.name, RequestBody.create("image/jpeg".toMediaType(), photoFile))
            .build()

        val request = Request.Builder()
            .url("http://192.168.1.8:5000/upload_photo") // IP DU RASPBERRY PI
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    RobotController.speak("Failed to connect to the server.")
                    requireView().findViewById<Button>(R.id.btnCapturePhoto).isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        activity?.runOnUiThread {
                            RobotController.speak("Server error processing photo.")
                            requireView().findViewById<Button>(R.id.btnCapturePhoto).isEnabled = true
                        }
                        return
                    }

                    val responseData = it.body?.string() ?: ""
                    try {
                        val json = JSONObject(responseData)
                        val qrUrl = json.optString("qr_url", "")

                        activity?.runOnUiThread {
                            if (qrUrl.isNotEmpty()) {
                                RobotController.speak("Here is your QR code! Scan it to download your photo.")
                                showQrCodeScreen(qrUrl)
                            } else {
                                RobotController.speak("Error generating QR code.")
                                requireView().findViewById<Button>(R.id.btnCapturePhoto).isEnabled = true
                            }
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            RobotController.speak("Data reading error.")
                            requireView().findViewById<Button>(R.id.btnCapturePhoto).isEnabled = true
                        }
                    }
                }
            }
        })
    }

    private fun showQrCodeScreen(qrUrl: String) {
        val qrCodeOverlay = requireView().findViewById<View>(R.id.qrCodeOverlay)
        val qrCodeImage = requireView().findViewById<ImageView>(R.id.qrCodeImage)

        qrCodeOverlay.visibility = View.VISIBLE
        TelemetryClient.track("qr_shown")

        if (isAdded) {
            Glide.with(this).load(qrUrl).into(qrCodeImage)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val viewFinder = requireView().findViewById<PreviewView>(R.id.viewFinder)

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("PhotoPage", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun goBackToMainPage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainPage())
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}