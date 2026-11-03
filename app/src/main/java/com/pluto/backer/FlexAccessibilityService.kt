package com.pluto.backer

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class FlexAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var wasInAmazonFlex = false
    private var backNavigationStartTime = 0L

    private val bringBackRunnable = Runnable {
        bringAmazonFlexToForeground()
    }

    private val backToMapRunnable = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - backNavigationStartTime
            if (elapsed >= 2000L) {
                Log.d(TAG, "Back navigation 2s timeout reached. Stopping back presses.")
                return
            }

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (rootNode.packageName == "com.amazon.flex.rabbit") {
                    if (isAtNavigationActivity(rootNode)) {
                        Log.d(TAG, "TRAVEL screen reached! Stopping back presses.")
                        return
                    }
                    Log.d(TAG, "In Amazon Flex; TRAVEL not yet reached. Pressing BACK (elapsed=${elapsed}ms).")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }

            handler.postDelayed(this, 180L)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        Log.d(TAG, "Event: pkg=$packageName, class=$className, type=${event.eventType}")

        if (packageName == "com.amazon.flex.rabbit") {
            wasInAmazonFlex = true
            val rootNode = rootInActiveWindow
            if (rootNode != null && isAtNavigationActivity(rootNode)) {
                handler.removeCallbacks(backToMapRunnable)
            }
        } else if (isDialerPackage(packageName) || className.contains("InCall", ignoreCase = true) || className.contains("Dialer", ignoreCase = true)) {
            if (wasInAmazonFlex) {
                val rootNode = rootInActiveWindow
                if (rootNode != null && isTargetCallScreen(rootNode, className)) {
                    Log.d(TAG, "Active Amazon Flex call to $TARGET_PHONE_NUMBER detected. Scheduling return in 1.5s.")
                    wasInAmazonFlex = false
                    handler.removeCallbacks(bringBackRunnable)
                    handler.postDelayed(bringBackRunnable, 1500)
                }
            }
        } else if (!isSystemPackage(packageName)) {
            wasInAmazonFlex = false
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(bringBackRunnable)
        handler.removeCallbacks(backToMapRunnable)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "FlexAccessibilityService connected")
    }

    private fun isDialerPackage(packageName: String): Boolean {
        return packageName.contains("dialer", ignoreCase = true) ||
                packageName.contains("phone", ignoreCase = true) ||
                packageName.contains("telephony", ignoreCase = true) ||
                packageName.contains("telecom", ignoreCase = true) ||
                packageName.contains("incall", ignoreCase = true)
    }

    private fun bringAmazonFlexToForeground() {
        Log.d(TAG, "Bringing Amazon Flex to foreground...")
        var intent = packageManager.getLaunchIntentForPackage("com.amazon.flex.rabbit")
        if (intent == null) {
            intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = "com.amazon.flex.rabbit"
                component = ComponentName(
                    "com.amazon.flex.rabbit",
                    "com.amazon.rabbit.android.presentation.core.LaunchActivity"
                )
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
            handler.removeCallbacks(backToMapRunnable)
            handler.postDelayed({
                backNavigationStartTime = SystemClock.uptimeMillis()
                backToMapRunnable.run()
            }, 350L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Amazon Flex", e)
        }
    }

    private fun isAtNavigationActivity(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if ("TRAVEL".equals(text, ignoreCase = true) ||
            "TRAVEL".equals(contentDesc, ignoreCase = true) ||
            text.contains("TRAVEL", ignoreCase = true) ||
            contentDesc.contains("TRAVEL", ignoreCase = true) ||
            (viewId.endsWith("toolbar_title") && "TRAVEL".equals(text, ignoreCase = true)) ||
            viewId.endsWith("map_fragment_holder") ||
            viewId.endsWith("navigationViewV2")) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    if (isAtNavigationActivity(child)) {
                        return true
                    }
                } finally {
                }
            }
        }
        return false
    }

    private fun isCallAudioActive(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        return mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
    }

    private fun isDialerBrowsingScreen(node: AccessibilityNodeInfo?, className: String): Boolean {
        if (className.contains("DialtactsActivity", ignoreCase = true) ||
            className.contains("CallLog", ignoreCase = true) ||
            className.contains("Contacts", ignoreCase = true) ||
            className.contains("Dialpad", ignoreCase = true)) {
            return true
        }
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if (viewId.contains("search_box") ||
            viewId.contains("bottom_nav") ||
            viewId.contains("dialtacts_main_view") ||
            text.equals("Search contacts", ignoreCase = true) ||
            contentDesc.equals("Search contacts", ignoreCase = true) ||
            text.equals("Favourites", ignoreCase = true) ||
            contentDesc.equals("Favourites", ignoreCase = true) ||
            text.equals("Keypad", ignoreCase = true) ||
            contentDesc.equals("Keypad", ignoreCase = true)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    if (isDialerBrowsingScreen(child, className)) {
                        return true
                    }
                } finally {
                }
            }
        }
        return false
    }

    private fun hasInCallControls(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if (viewId.contains("end_call") ||
            viewId.contains("incall") ||
            viewId.contains("contact_grid_middle_row") ||
            contentDesc.contains("End call", ignoreCase = true) ||
            text.contains("End call", ignoreCase = true) ||
            contentDesc.contains("Hang up", ignoreCase = true) ||
            text.contains("Calling…", ignoreCase = true) ||
            text.contains("Calling...", ignoreCase = true) ||
            text.contains("Ongoing call", ignoreCase = true) ||
            text.contains("Dialing", ignoreCase = true)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    if (hasInCallControls(child)) {
                        return true
                    }
                } finally {
                }
            }
        }
        return false
    }

    private fun containsTargetPhoneNumber(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        val digitsText = text.replace(Regex("[^0-9]"), "")
        val digitsDesc = contentDesc.replace(Regex("[^0-9]"), "")

        if (digitsText.contains(TARGET_PHONE_NUMBER) ||
            digitsDesc.contains(TARGET_PHONE_NUMBER) ||
            text.contains(TARGET_PHONE_NUMBER) ||
            contentDesc.contains(TARGET_PHONE_NUMBER) ||
            (viewId.contains("contact_grid_middle_row") && (digitsText.contains(TARGET_PHONE_NUMBER) || digitsDesc.contains(TARGET_PHONE_NUMBER)))) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    if (containsTargetPhoneNumber(child)) {
                        return true
                    }
                } finally {
                }
            }
        }
        return false
    }

    private fun isTargetCallScreen(node: AccessibilityNodeInfo?, className: String): Boolean {
        if (node == null) return false
        if (isDialerBrowsingScreen(node, className)) {
            return false
        }
        val isCallActive = isCallAudioActive() ||
                className.contains("InCall", ignoreCase = true) ||
                className.contains("telecom", ignoreCase = true) ||
                hasInCallControls(node)

        if (!isCallActive) {
            return false
        }
        return containsTargetPhoneNumber(node)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName == "android" ||
                packageName == "com.android.systemui" ||
                packageName.contains("inputmethod") ||
                packageName == "com.pluto.backer"
    }

    companion object {
        private const val TAG = "FlexAccessibility"
        private const val TARGET_PHONE_NUMBER = "7961734555"
    }
}
