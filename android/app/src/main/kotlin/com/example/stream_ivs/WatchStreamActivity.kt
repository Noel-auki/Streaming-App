package com.example.stream_ivs

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.ivs.player.*

class WatchStreamActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var qualitySpinner: Spinner
    private lateinit var urlField: EditText
    private lateinit var loadButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.watch_stream_activity)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.player_view)
        playerView.controlsEnabled = false
        urlField = findViewById(R.id.urlField)
        loadButton = findViewById(R.id.loadButton)
        pauseButton = findViewById(R.id.pauseButton)
        resumeButton = findViewById(R.id.resumeButton)
        // Set the default URL in the input field
        urlField.setText("https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8")
        setupButtons()
        handlePlayerEvents()
        setupQualitySpinner()
    }

    private fun setupButtons() {
        loadButton.setOnClickListener {
            val urlText = urlField.text.toString()
            if (urlText.isNotEmpty()) {
                loadAndPlayStream(urlText)
            } else {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            }
        }

        pauseButton.setOnClickListener {
            if (playerView.player.state == Player.State.PLAYING) {
                playerView.player.pause()
                pauseButton.isSelected = true
                resumeButton.isSelected = false
            }
        }

        resumeButton.setOnClickListener {
            if (playerView.player.state == Player.State.READY || playerView.player.state == Player.State.IDLE) {
                playerView.player.play()
                resumeButton.isSelected = true
                pauseButton.isSelected = false
            }
        }
    }

    private fun loadAndPlayStream(url: String) {
        playerView.resizeMode = ResizeMode.FILL
        playerView.player.load(Uri.parse(url))
        playerView.player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player.release()
    }

    private fun setupQualitySpinner() {
        qualitySpinner = findViewById(R.id.qualitySpinner)
        // Initial setup for spinner
    }

    private fun updateQuality() {
        val qualities = playerView.player.qualities.map { it.name }
        val auto = "Auto (${playerView.player.quality.name})"
        val qualityAdapter = ArrayAdapter(this, R.layout.spinner_item, listOf(auto) + qualities)
        qualityAdapter.setDropDownViewResource(R.layout.spinner_item)
        qualitySpinner.adapter = qualityAdapter
        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedQuality = parent.getItemAtPosition(position) as String
                    val quality = playerView.player.qualities.firstOrNull { it.name == selectedQuality }
                    quality?.let {
                        // Explicitly set auto quality mode to false
                        playerView.player.setAutoQualityMode(false)
                        // Set the chosen quality
                        playerView.player.setQuality(it)
                    }

            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Optional: Reset to default behavior if nothing is selected
                playerView.player.setAutoQualityMode(true)
            }
        }
    }

    private fun handlePlayerEvents() {
        playerView.player.addListener(object : Player.Listener() {
            override fun onCue(p0: Cue) {}

            override fun onDurationChanged(p0: Long) {}

            override fun onStateChanged(state: Player.State) {
                when (state) {
                    Player.State.READY -> updateQuality()
                    else -> {}
                }
            }

            override fun onError(p0: PlayerException) {
                Toast.makeText(this@WatchStreamActivity, "Playback error: ${p0.message}", Toast.LENGTH_LONG).show()
            }

            override fun onRebuffering() {}

            override fun onSeekCompleted(p0: Long) {}

            override fun onVideoSizeChanged(p0: Int, p1: Int) {}


            override fun onQualityChanged(quality: Quality) {
                updateQuality() // Update the spinner when quality options change
            }
        })
    }
}
