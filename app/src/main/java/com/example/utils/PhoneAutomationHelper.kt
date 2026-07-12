package com.example.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object PhoneAutomationHelper {
    private const val TAG = "PhoneAutomationHelper"

    fun toggleFlashlight(context: Context, enabled: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enabled)
                Log.d(TAG, "Flashlight set to $enabled")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight: ${e.message}")
            false
        }
    }

    fun launchApp(context: Context, query: String): String {
        val appName = query.lowercase()
        val packageManager = context.packageManager
        
        // Map common app names to typical packages
        val packageMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "camera" to "android.media.action.IMAGE_CAPTURE", // Action-based for custom camera apps
            "settings" to Settings.ACTION_SETTINGS, // Intent action
            "maps" to "com.google.android.apps.maps",
            "whatsapp" to "com.whatsapp",
            "contacts" to "com.android.contacts",
            "gmail" to "com.google.android.gm"
        )

        try {
            // Check direct intent actions first for settings/camera
            if (appName.contains("settings")) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Opening Settings, Sir."
            } else if (appName.contains("camera")) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MAPS)
                    action = android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Initializing camera capture sequence, Sir."
            }

            // Search matches in mapped package names
            for ((key, packageName) in packageMap) {
                if (appName.contains(key)) {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return "Launching ${key.replaceFirstChar { it.uppercase() }} now, Sir."
                    }
                }
            }

            // Fallback: search among all installed apps with matching name
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = packageManager.queryIntentActivities(mainIntent, 0)
            for (app in apps) {
                val appLabel = app.loadLabel(packageManager).toString().lowercase()
                if (appName.contains(appLabel) || appLabel.contains(appName)) {
                    val intent = packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return "Launching ${app.loadLabel(packageManager)}, Boss."
                    }
                }
            }

            // If not found, open Play Store or search web
            val searchUri = Uri.parse("https://www.google.com/search?q=$query")
            val webIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return "Application not found locally. Searching web for \"$query\", Sir."

        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${e.message}")
            return "Unable to launch requested application, Sir."
        }
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, message: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm scheduled for %02d:%02d, Sir.".format(hour, minute)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm: ${e.message}")
            "Failed to schedule alarm automatically, Sir."
        }
    }

    fun sendEmail(context: Context, emailAddress: String, subject: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Drafting your email to $emailAddress, Sir."
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email: ${e.message}")
            "Could not open mail compose client, Sir."
        }
    }

    fun initiateCall(context: Context, phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Dialing contact sequence, Sir."
        } catch (e: Exception) {
            Log.e(TAG, "Error calling: ${e.message}")
            "Unable to open call terminal, Boss."
        }
    }

    fun changeBrightness(context: Context, level: Float) {
        // Since modify settings is a protected system setting, we notify the user
        // and open the Display settings overlay for manual tuning!
        try {
            Toast.makeText(context, "Adjusting brightness terminal, Sir", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying brightness settings: ${e.message}")
        }
    }

    fun toggleWifiOrBluetooth(context: Context, isWifi: Boolean) {
        // Opens respective settings pane for fast toggle. Modern Android prevents background apps from changing Wifi/Bluetooth state directly.
        try {
            val action = if (isWifi) Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_BLUETOOTH_SETTINGS
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling connectivity settings: ${e.message}")
        }
    }

    fun openBatterySaverSettings(context: Context): String {
        return try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Battery Saver configuration, Sir."
        } catch (e: Exception) {
            try {
                // Fallback to Power Usage Summary
                val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening battery usage details, Sir."
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open battery settings: ${ex.message}")
                "I was unable to open battery settings directly on this device, Boss."
            }
        }
    }
}
