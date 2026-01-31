package com.example.blocker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import android.graphics.Color
import android.util.Log

class BlockedActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BlockedActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val blockedType = intent?.getStringExtra("blocked_type") ?: "app"
            val blockedName = intent?.getStringExtra("blocked_name") ?: ""

            Log.d(TAG, "Blocked activity shown for type=$blockedType, name=$blockedName")

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.WHITE)
                setPadding(48, 48, 48, 48)
            }

            val icon = TextView(this).apply {
                text = if (blockedType == "website") "🌐" else "🚫"
                textSize = 64f
                gravity = Gravity.CENTER
            }

            val title = TextView(this).apply {
                text = if (blockedType == "website") "Website Blocked" else "App Blocked"
                textSize = 28f
                setTextColor(Color.parseColor("#D32F2F"))
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 16)
            }

            val message = TextView(this).apply {
                text = if (blockedType == "website" && blockedName.isNotEmpty()) {
                    "The website \"$blockedName\" has been blocked by BlockerApp."
                } else if (blockedName.isNotEmpty()) {
                    "The app \"$blockedName\" has been blocked by BlockerApp."
                } else {
                    "This content has been blocked by BlockerApp."
                }
                textSize = 16f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            }

            val homeButton = Button(this).apply {
                text = "Go to Home Screen"
                setOnClickListener {
                    goToHomeScreen()
                }
            }

            layout.addView(icon)
            layout.addView(title)
            layout.addView(message)
            layout.addView(homeButton)

            setContentView(layout)

            // Handle back press using the modern callback API
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goToHomeScreen()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            goToHomeScreen()
        }
    }

    private fun goToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error going to home screen", e)
        }
        finish()
    }
}
