package com.example.stream_ivs

import android.content.Intent
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.stream_ivs/ivs"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            // Handle method calls here
            if (call.method == "startStream") {
                Log.d("IVS", "Starting stream")
                val intent = Intent(this, StartLiveStreamActivity::class.java)
                startActivity(intent)
                result.success("Starting Stream")
            } else if (call.method == "watchStream") {
                Log.d("IVS", "Watching stream")
                val intent = Intent(this, WatchStreamActivity::class.java)
                startActivity(intent)
                result.success("Watching Stream")
            } else {
                result.notImplemented()
            }
        }
    }
}

