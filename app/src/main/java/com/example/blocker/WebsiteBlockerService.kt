package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WebsiteBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "WebsiteBlockerService"
        private const val BLOCK_COOLDOWN_MS = 3000L
        private const val MAX_NODE_DEPTH = 15
        
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.samsung.android.app.sbrowser"
        )
        
        @Volatile
        var isRunning = false
            private set
    }

    private val lastBlockedTime = AtomicLong(0L)
    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastBlockedUrl = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                       AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 200
                packageNames = BROWSER_PACKAGES.toTypedArray()
            }
            serviceInfo = info
            isRunning = true
            Log.d(TAG, "Accessibility Service connected")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Prevent concurrent processing
        if (!isProcessing.compareAndSet(false, true)) return
        
        try {
            val packageName = event.packageName?.toString() ?: return
            if (packageName !in BROWSER_PACKAGES) return
            
            val rootNode = rootInActiveWindow ?: return
            
            try {
                val url = extractUrlFromBrowser(rootNode, packageName)
                if (!url.isNullOrEmpty()) {
                    checkAndBlockUrl(url)
                }
            } finally {
                safeRecycle(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        } finally {
            isProcessing.set(false)
        }
    }

    private fun extractUrlFromBrowser(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        val urlBarIds = listOf(
            "$packageName:id/url_bar",
            "$packageName:id/url_field", 
            "$packageName:id/search_box_text",
            "$packageName:id/url",
            "$packageName:id/address_bar_edit_text",
            "$packageName:id/mozac_browser_toolbar_url_view",
            "$packageName:id/omnibox_text",
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text"
        )
        
        for (urlBarId in urlBarIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(urlBarId)
                if (nodes != null && nodes.isNotEmpty()) {
                    val text = nodes[0].text?.toString()
                    nodes.forEach { safeRecycle(it) }
                    if (!text.isNullOrEmpty()) {
                        return text
                    }
                }
            } catch (e: Exception) {
                // Continue to next ID
            }
        }
        
        return findUrlInNode(rootNode, 0)
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > MAX_NODE_DEPTH) return null
        
        try {
            val text = node.text?.toString()
            if (text != null && isUrl(text)) {
                return text
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val result = findUrlInNode(child, depth + 1)
                    if (result != null) return result
                } finally {
                    safeRecycle(child)
                }
            }
        } catch (e: Exception) {
            // Ignore and return null
        }
        
        return null
    }
    
    private fun isUrl(text: String): Boolean {
        return text.contains(".") && 
               (text.contains(".com") || text.contains(".org") || 
                text.contains(".net") || text.contains(".io") ||
                text.contains("www.") || text.startsWith("http"))
    }

    private fun checkAndBlockUrl(url: String) {
        val blockedSites = try {
            BlockPreferences.getBlockedWebsites(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting blocked websites", e)
            return
        }
        
        if (blockedSites.isEmpty()) return
        
        val normalizedUrl = normalizeUrl(url)
        
        for (blocked in blockedSites) {
            val normalizedBlocked = normalizeUrl(blocked)
            
            if (normalizedUrl.contains(normalizedBlocked) || 
                normalizedUrl.startsWith(normalizedBlocked) ||
                normalizedUrl.split("/").firstOrNull()?.contains(normalizedBlocked) == true) {
                
                val now = System.currentTimeMillis()
                val lastBlocked = lastBlockedTime.get()
                
                if (now - lastBlocked > BLOCK_COOLDOWN_MS || lastBlockedUrl != blocked) {
                    if (lastBlockedTime.compareAndSet(lastBlocked, now)) {
                        lastBlockedUrl = blocked
                        Log.i(TAG, "BLOCKING website: $url (matched: $blocked)")
                        launchBlockedActivity(blocked)
                    }
                }
                return
            }
        }
    }
    
    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun launchBlockedActivity(blockedSite: String) {
        try {
            val intent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_type", "website")
                putExtra("blocked_name", blockedSite)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching blocked activity", e)
        }
    }
    
    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                @Suppress("DEPRECATION")
                node?.recycle()
            } catch (e: Exception) {
                // Ignore recycle errors
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        Log.d(TAG, "Accessibility Service destroyed")
        super.onDestroy()
    }
}
