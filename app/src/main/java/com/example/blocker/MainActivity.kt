package com.example.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var addBtn: Button
    private lateinit var pkgInput: EditText
    private lateinit var blockedListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val blockedPackages = mutableListOf<String>()

    // Website blocking
    private lateinit var websiteInput: EditText
    private lateinit var addWebsiteBtn: Button
    private lateinit var blockedWebsitesListView: ListView
    private lateinit var websiteAdapter: ArrayAdapter<String>
    private val blockedWebsites = mutableListOf<String>()
    private lateinit var accessibilityBtn: Button
    private lateinit var disableBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            initializeViews()
            setupAdapters()
            setupClickListeners()
            refreshBlockedLists()
            updateButtonStates()
            requestDeviceAdminIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestDeviceAdminIfNeeded() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
            val adminComponent = android.content.ComponentName(this, BlockerDeviceAdminReceiver::class.java)
            val isAdmin = dpm.isAdminActive(adminComponent)
            if (!isAdmin) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "BlockerApp needs device admin to prevent easy uninstall.")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting device admin", e)
        }
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        addBtn = findViewById(R.id.addBtn)
        pkgInput = findViewById(R.id.pkgInput)
        blockedListView = findViewById(R.id.blockedListView)

        websiteInput = findViewById(R.id.websiteInput)
        addWebsiteBtn = findViewById(R.id.addWebsiteBtn)
        blockedWebsitesListView = findViewById(R.id.blockedWebsitesListView)
        accessibilityBtn = findViewById(R.id.accessibilityBtn)
        disableBtn = findViewById(R.id.disableBtn)
    }

    private fun setupAdapters() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blockedPackages)
        blockedListView.adapter = adapter

        websiteAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blockedWebsites)
        blockedWebsitesListView.adapter = websiteAdapter
    }

    private fun setupClickListeners() {
        // Long press to remove a blocked package
        blockedListView.setOnItemLongClickListener { _, _, position, _ ->
            try {
                if (position < blockedPackages.size) {
                    val pkg = blockedPackages[position]
                    BlockPreferences.removeBlockedPackage(this, pkg)
                    refreshBlockedLists()
                    Toast.makeText(this, "Removed $pkg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing package", e)
            }
            true
        }

        // Long press to remove a blocked website
        blockedWebsitesListView.setOnItemLongClickListener { _, _, position, _ ->
            try {
                if (position < blockedWebsites.size) {
                    val site = blockedWebsites[position]
                    BlockPreferences.removeBlockedWebsite(this, site)
                    refreshBlockedLists()
                    Toast.makeText(this, "Removed $site", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing website", e)
            }
            true
        }

        startBtn.setOnClickListener {
            try {
                if (!hasUsageStatsPermission()) {
                    Toast.makeText(this, "Please grant Usage Access permission", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    val intent = Intent(this, AppMonitorService::class.java)
                    ContextCompat.startForegroundService(this, intent)
                    Toast.makeText(this, "App monitor started", Toast.LENGTH_SHORT).show()

                    // Prompt user to whitelist battery optimizations for better persistence
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                            val isWhitelisted = pm?.isIgnoringBatteryOptimizations(packageName) == true
                            if (!isWhitelisted) {
                                val whitelist = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                startActivity(whitelist)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to prompt battery optimization whitelist", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service", e)
                Toast.makeText(this, "Error starting monitor", Toast.LENGTH_SHORT).show()
            }
        }

        stopBtn.setOnClickListener {
            try {
                stopService(Intent(this, AppMonitorService::class.java))
                Toast.makeText(this, "App monitor stopped", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping service", e)
            }
        }

        addBtn.setOnClickListener {
            val pkg = pkgInput.text?.toString()?.trim() ?: ""
            if (pkg.isNotEmpty()) {
                BlockPreferences.addBlockedPackage(this, pkg)
                pkgInput.text?.clear()
                refreshBlockedLists()
                Toast.makeText(this, "Added $pkg", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter a package name", Toast.LENGTH_SHORT).show()
            }
        }

        addWebsiteBtn.setOnClickListener {
            val site = websiteInput.text?.toString()?.trim() ?: ""
            if (site.isNotEmpty()) {
                BlockPreferences.addBlockedWebsite(this, site)
                websiteInput.text?.clear()
                refreshBlockedLists()
                Toast.makeText(this, "Added $site", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter a website", Toast.LENGTH_SHORT).show()
            }
        }

        accessibilityBtn.setOnClickListener {
            try {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "Enable BlockerApp in Accessibility settings", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    Toast.makeText(this, "Accessibility already enabled!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening accessibility settings", e)
            }
        }

        disableBtn.setOnClickListener {
            try {
                // Generate a longer random token and require manual input to disable
                val token = generateToken(20)
                val input = EditText(this)
                val dialog = AlertDialog.Builder(this)
                    .setTitle("Confirm Disable")
                    .setMessage("To disable monitoring, type the following token exactly:\n\n$token")
                    .setView(input)
                    .setPositiveButton("Disable") { d, _ ->
                        val entered = input.text?.toString() ?: ""
                        if (entered == token) {
                            try {
                                stopService(Intent(this, AppMonitorService::class.java))
                                Toast.makeText(this, "Monitor disabled", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error disabling monitor", e)
                                Toast.makeText(this, "Failed to disable monitor", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Token mismatch — monitor remains enabled", Toast.LENGTH_SHORT).show()
                        }
                        d.dismiss()
                    }
                    .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                    .create()
                dialog.show()
            } catch (e: Exception) {
                Log.e(TAG, "Error in disable flow", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            refreshBlockedLists()
            updateStatus()
            updateButtonStates()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }

    private fun refreshBlockedLists() {
        try {
            blockedPackages.clear()
            blockedPackages.addAll(BlockPreferences.getBlockedPackages(this))
            adapter.notifyDataSetChanged()

            blockedWebsites.clear()
            blockedWebsites.addAll(BlockPreferences.getBlockedWebsites(this))
            websiteAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing lists", e)
        }
    }

    private fun updateStatus() {
        try {
            val usageOk = hasUsageStatsPermission()
            val accessibilityOk = isAccessibilityServiceEnabled()
            val serviceRunning = AppMonitorService.isRunning

            val status = StringBuilder()
            status.append(if (usageOk) "✓ Usage Access" else "✗ Usage Access needed")
            status.append("\n")
            status.append(if (accessibilityOk) "✓ Accessibility" else "✗ Accessibility needed (for websites)")
            status.append("\n")
            status.append(if (serviceRunning) "● Monitor running" else "○ Monitor stopped")

            statusText.text = status.toString()

            accessibilityBtn.text = if (accessibilityOk) 
                "✓ Accessibility Enabled" 
            else 
                "Enable Accessibility (for websites)"
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status", e)
        }
    }

    private fun updateButtonStates() {
        try {
            val isRunning = AppMonitorService.isRunning
            startBtn.isEnabled = !isRunning
            stopBtn.isEnabled = isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Error updating button states", e)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            enabledServices.any { it.resolveInfo?.serviceInfo?.packageName == packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility", e)
            false
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats permission", e)
            false
        }
    }

    private fun generateToken(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // avoid ambiguous characters
        val rnd = SecureRandom()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)])
        }
        return sb.toString()
    }
}
