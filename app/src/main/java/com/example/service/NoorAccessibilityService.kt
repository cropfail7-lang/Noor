package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.d(TAG, "NoorAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            _activePackage.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "NoorAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isConnected.value = false
    }

    fun getScreenContentSummary(maxItems: Int = 25): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val items = mutableListOf<String>()
        collectVisibleText(root, items, maxItems)
        return items
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        maxItems: Int
    ) {
        if (node == null || out.size >= maxItems) return
        val label = node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (!label.isNullOrBlank() && label.length <= 140) {
            out.add(label)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= maxItems) break
            collectVisibleText(node.getChild(i), out, maxItems)
        }
    }

    fun clickNodeWithText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(targetText)
        for (node in matches) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }

    fun performSystemNavigation(actionName: String): Boolean {
        val action = when (actionName.lowercase().trim()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(action)
    }

    companion object {
        private const val TAG = "NoorAccessibility"

        @Volatile
        var instance: NoorAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _activePackage = MutableStateFlow("")
        val activePackage: StateFlow<String> = _activePackage.asStateFlow()

        fun isEnabledInSettings(context: Context): Boolean {
            val expectedComponent = ComponentName(context, NoorAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val componentString = splitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentString)
                if (enabledComponent == expectedComponent) return true
            }
            return instance != null
        }
    }
}
