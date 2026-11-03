package com.pluto.backer

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class FlexAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var wasInAmazonFlex = false
    private var isNavigatingBackToMap = false

    private val bringBackRunnable = Runnable {
        bringAmazonFlexToForeground()
    }

    private val backRunnable = object : Runnable {
        override fun run() {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (rootNode.packageName == "com.amazon.flex.rabbit") {
                    if (isAtNavigationActivity(rootNode)) {
                        isNavigatingBackToMap = false
                        return
                    }
                } else {
                    // Not in Amazon Flex yet, do not press back in another application!
                    handler.postDelayed(this, 300)
                    return
                }
            } else {
                handler.postDelayed(this, 300)
                return
            }
            if (isNavigatingBackToMap) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed(this, 400)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        Log.d(TAG, "Event: pkg=$packageName, class=$className, type=${event.eventType}")

        if (packageName == "com.amazon.flex.rabbit") {
            wasInAmazonFlex = true
            if (isNavigatingBackToMap) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    if (isAtNavigationActivity(rootNode)) {
                        isNavigatingBackToMap = false
                        handler.removeCallbacks(backRunnable)
                    }
                }
            }
        } else if (isDialerPackage(packageName) || className.contains("InCall", ignoreCase = true) || className.contains("Dialer", ignoreCase = true)) {
            if (wasInAmazonFlex) {
                Log.d(TAG, "Phone call screen detected while using Amazon Flex. Scheduling return in 1.5s.")
                wasInAmazonFlex = false
                handler.removeCallbacks(bringBackRunnable)
                handler.postDelayed(bringBackRunnable, 1500)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (rootNode.packageName == "com.amazon.flex.rabbit") {
                    if (!isAtNavigationActivity(rootNode)) {
                        isNavigatingBackToMap = true
                        handler.removeCallbacks(backRunnable)
                        handler.post(backRunnable)
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(bringBackRunnable)
        handler.removeCallbacks(backRunnable)
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
            isNavigatingBackToMap = true
            handler.removeCallbacks(backRunnable)
            handler.postDelayed(backRunnable, 800)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Amazon Flex", e)
        }
    }

    private fun isAtNavigationActivity(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if ("TRAVEL".equals(text, ignoreCase = true) ||
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
                    // child doesn't need explicit recycle in modern Android
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "FlexAccessibility"
    }
}
