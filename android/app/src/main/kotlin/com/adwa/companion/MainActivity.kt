package com.adwa.companion

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.adwa.companion/overlay"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "checkPermission" -> {
                        result.success(hasOverlayPermission())
                    }
                    "requestPermission" -> {
                        requestOverlayPermission()
                        result.success(true)
                    }
                    "startOverlay" -> {
                        if (hasOverlayPermission()) {
                            val intent = Intent(this, OverlayService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }
                    "stopOverlay" -> {
                        val intent = Intent(this, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_STOP
                        }
                        startService(intent)
                        stopService(Intent(this, OverlayService::class.java))
                        result.success(true)
                    }
                    "isRunning" -> {
                        result.success(OverlayService.isRunning)
                    }
                    "dismissApp" -> {
                        finishAffinity()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}
