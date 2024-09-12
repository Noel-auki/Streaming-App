package com.example.stream_ivs

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amazonaws.ivs.broadcast.*

class StartLiveStreamActivity : AppCompatActivity() {
    private lateinit var recordButton: ImageButton
    private lateinit var spinner: Spinner
    private lateinit var broadcastSession: BroadcastSession
    private lateinit var previewHolder: LinearLayout
    private var isStreaming = false
    private var currentCamera: Device? = null
    private var microphoneDevice: Device? = null
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureSession: CameraCaptureSession

    private val broadcastListener = object : BroadcastSession.Listener() {
        override fun onStateChanged(state: BroadcastSession.State) {
            Log.d(TAG, "State=$state")
        }

        override fun onError(exception: BroadcastException) {
            Log.e(TAG, "Exception: $exception")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_live_stream)

        previewHolder = findViewById(R.id.previewHolder)
        recordButton = findViewById(R.id.recordButton)
        spinner = findViewById(R.id.cameraSystemSpinner)

        setupSpinner()

        if (allPermissionsGranted()) {
            setupBroadcastSession()
        } else {
            requestPermissions()
        }

        recordButton.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
            } else {
                startStreaming()
            }
        }
    }

    private fun setupSpinner() {
        val cameraSystems = arrayOf(
            "Default Camera", "Front Camera", "Wide Angle Camera", "Telephoto Camera",
            "UltraWide Camera", "LiDAR Depth Camera"
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, cameraSystems)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @RequiresApi(Build.VERSION_CODES.R)
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val cameraType = parent.getItemAtPosition(position).toString()
                switchCameraSystem(cameraType)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun switchCameraSystem(cameraType: String) {
        when (cameraType) {
            "Default Camera" -> attachCamera(Device.Descriptor.Position.BACK)
            "Front Camera" -> attachCamera(Device.Descriptor.Position.FRONT)
            "Wide Angle Camera" -> attachCameraById("2")  // Attach Back Camera (2) explicitly for wide-angle
            "Telephoto Camera" -> attachCameraById("4")
            "UltraWide Camera" -> attachCameraById("2")
            "LiDAR Depth Camera" -> attachCameraById("5")
            else -> {
                Toast.makeText(this, "Switching to default camera", Toast.LENGTH_SHORT).show()
                spinner.setSelection(0) // Set spinner to "Default Camera" (assuming it's at index 0)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setZoomLevel(zoomLevel: Float) {
        currentCamera?.let {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(it.descriptor.deviceId)
            val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)

            if (zoomRange != null && zoomLevel in zoomRange.lower..zoomRange.upper) {
                try {
                    val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomLevel)
                    }
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    Log.d(TAG, "Zoom level supported. Setting zoom to $zoomLevel")
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying zoom: ${e.message}")
                }
            } else {
                Log.e(TAG, "Zoom level $zoomLevel is not supported by this camera.")
            }
        } ?: run {
            Log.e(TAG, "No camera available to apply zoom.")
        }
    }

    private fun attachCameraById(cameraId: String) {
        detachCurrentCamera()

        val availableDevices = BroadcastSession.listAvailableDevices(this)
        val cameraDevice = availableDevices.firstOrNull { it.deviceId == cameraId }

        cameraDevice?.let {
            try {
                broadcastSession.attachDevice(it) { device ->
                    setupCameraPreview(device)
                    currentCamera = device  // Track the newly attached camera
                    Log.d(TAG, "Successfully attached camera with ID $cameraId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach camera with ID $cameraId: ${e.message}")
                Toast.makeText(this, "Failed to attach camera with ID $cameraId", Toast.LENGTH_SHORT).show()
                attachCamera(Device.Descriptor.Position.BACK)
                spinner.setSelection(0)
            }
        } ?: run {
            Toast.makeText(this, "Camera not found, switching to default.", Toast.LENGTH_SHORT).show()
            attachCamera(Device.Descriptor.Position.BACK)
            spinner.setSelection(0)
        }
    }

    private fun attachCamera(position: Device.Descriptor.Position, onAttached: (() -> Unit)? = null) {
        detachCurrentCamera()

        val availableDevices = BroadcastSession.listAvailableDevices(this)
        val cameraDevice = availableDevices.firstOrNull {
            it.position == position && it.type == Device.Descriptor.DeviceType.CAMERA
        }

        cameraDevice?.let {
            try {
                broadcastSession.attachDevice(it) { device ->
                    setupCameraPreview(device)
                    currentCamera = device  // Track the newly attached camera
                    Log.d(TAG, "Successfully attached camera at position $position")
                    onAttached?.invoke()  // Execute the callback after attaching the camera
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach camera at position $position: ${e.message}")
                Toast.makeText(this, "Failed to attach camera at position $position", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Camera not found, switching to default.", Toast.LENGTH_SHORT).show()
            attachCamera(Device.Descriptor.Position.BACK)
            spinner.setSelection(0)
        }
    }

    private fun attachSpecificCamera(cameraKeyword: String) {
        val availableDevices = BroadcastSession.listAvailableDevices(this)
        val cameraDevice = availableDevices.firstOrNull {
            it.friendlyName.contains(cameraKeyword, ignoreCase = true) && it.type == Device.Descriptor.DeviceType.CAMERA
        }

        cameraDevice?.let {
            broadcastSession.attachDevice(it) { device ->
                setupCameraPreview(device)
                currentCamera = device
            }
        } ?: run {
            Toast.makeText(this, "Camera type not supported, switching to default.", Toast.LENGTH_SHORT).show()
            attachCamera(Device.Descriptor.Position.BACK)
            spinner.setSelection(0)
        }
    }

    private fun detachCurrentCamera() {
        currentCamera?.let {
            try {
                broadcastSession.detachDevice(it)  // Detach current camera
                currentCamera = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to detach current camera: ${e.message}")
            }
        }
    }

    private fun setupCameraPreview(device: Device) {
        previewHolder.removeAllViews()
        val preview = (device as ImageDevice).previewView.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewHolder.addView(preview)
    }

    private fun setupBroadcastSession() {
        val context = applicationContext

        val config = BroadcastConfiguration.with { `$`: BroadcastConfiguration ->
            // Set audio bitrate for microphone
            `$`.audio.bitrate = 128000

            // Video settings to accommodate portrait view
            `$`.video.maxBitrate = 3500000
            `$`.video.minBitrate = 500000
            `$`.video.initialBitrate = 1500000
            `$`.video.setSize(720, 1280)  // Adjusted for portrait resolution (720x1280)

            // Define the mixer slots for video and audio
            `$`.mixer.slots =
                arrayOf(
                    BroadcastConfiguration.Mixer.Slot.with { slot: BroadcastConfiguration.Mixer.Slot ->
                        // Audio slot configuration
                        slot.preferredAudioInput = Device.Descriptor.DeviceType.MICROPHONE  // Ensure microphone is bound here
                        slot.preferredVideoInput = Device.Descriptor.DeviceType.UNKNOWN  // No video source bound to this slot
                        slot.name = "audio"  // Name the audio slot
                        slot
                    },
                    BroadcastConfiguration.Mixer.Slot.with { slot: BroadcastConfiguration.Mixer.Slot ->
                        // Camera slot configuration for portrait video
                        slot.setzIndex(1)
                        slot.aspect = BroadcastConfiguration.AspectMode.FIT  // FIT or COVER ensures proper portrait aspect ratio
                        slot.setSize(720, 1280)  // Match portrait resolution for the camera
                        slot.preferredVideoInput = Device.Descriptor.DeviceType.CAMERA  // Ensure camera is bound here
                        slot.name = "camera"  // Name the camera slot
                        slot
                    }
                )
            `$`
        }


        // Create broadcast session with the preset config
        broadcastSession = BroadcastSession(
            context,
            broadcastListener,
            config,
            null
        )

        broadcastSession.awaitDeviceChanges {
            var cameraAttached = false
            var microphoneAttached = false

            // Iterate through available devices to attach both camera and microphone
            for (desc in BroadcastSession.listAvailableDevices(context)) {
                when (desc.type) {
                    Device.Descriptor.DeviceType.CAMERA -> {
                        if (!cameraAttached && desc.position == Device.Descriptor.Position.FRONT) {
                            broadcastSession.attachDevice(desc) { device ->
                                setupCameraPreview(device)
                                currentCamera = device
                                cameraAttached = true
                                Log.d(TAG, "Camera attached: ${desc.type}")
                            }
                        }
                    }
                    Device.Descriptor.DeviceType.MICROPHONE -> {
                        if (!microphoneAttached) {
                            broadcastSession.attachDevice(desc) { device ->
                                microphoneDevice = device
                                Log.d(TAG, "Microphone attached successfully: $microphoneDevice")
                                // Bind the microphone to the mixer for audio
                                broadcastSession.getMixer().bind(device, "audio")
                                microphoneAttached = true
                            }
                        }
                    }

                    Device.Descriptor.DeviceType.UNKNOWN -> TODO()
                    Device.Descriptor.DeviceType.SCREEN -> TODO()
                    Device.Descriptor.DeviceType.SYSTEM_AUDIO -> TODO()
                    Device.Descriptor.DeviceType.USER_IMAGE -> TODO()
                    Device.Descriptor.DeviceType.USER_AUDIO -> TODO()
                }
            }

            if (!cameraAttached) {
                Log.e(TAG, "Failed to attach camera device")
            }
            if (!microphoneAttached) {
                Log.e(TAG, "Failed to attach microphone device")
            }
        }
    }
    private fun startStreaming() {
        val streamKey = "sk_us-east-1_HA7T2xK1JsPr_llbFvIQa2repQS9Rwkkf9rNSflTnrp"
        val rtmpsUrl = "rtmps://3893e27cd44d.global-contribute.live-video.net:443/app/"
        broadcastSession.start(rtmpsUrl, streamKey)
        recordButton.setBackgroundResource(R.drawable.recording_button)
        isStreaming = true
    }

    private fun stopStreaming() {
        broadcastSession.stop()
        recordButton.setBackgroundResource(R.drawable.not_recording_button)
        isStreaming = false
    }

    private fun allPermissionsGranted() = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            setupBroadcastSession()
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcastSession.release()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = "StartLiveStreamActivity"
    }
}
