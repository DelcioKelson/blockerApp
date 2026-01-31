package com.example.blocker

import android.content.Context
import android.util.Log

/**
 * Thread-safe preferences manager for blocked packages and websites.
 * All operations are synchronized to prevent race conditions.
 */
object BlockPreferences {
    private const val TAG = "BlockPreferences"
    private const val PREFS = "blocker_prefs"
    private const val KEY_PACKAGES = "blocked_packages"
    private const val KEY_WEBSITES = "blocked_websites"
    private val lock = Any()

    // ===== App blocking =====
    fun addBlockedPackage(ctx: Context, packageName: String): Boolean {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val currentSet = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
                val newSet = HashSet(currentSet)
                val added = newSet.add(packageName.trim())
                prefs.edit().putStringSet(KEY_PACKAGES, newSet).apply()
                Log.d(TAG, "Added package: $packageName, success=$added")
                added
            } catch (e: Exception) {
                Log.e(TAG, "Error adding package: $packageName", e)
                false
            }
        }
    }

    fun removeBlockedPackage(ctx: Context, packageName: String): Boolean {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val currentSet = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
                val newSet = HashSet(currentSet)
                val removed = newSet.remove(packageName)
                prefs.edit().putStringSet(KEY_PACKAGES, newSet).apply()
                Log.d(TAG, "Removed package: $packageName, success=$removed")
                removed
            } catch (e: Exception) {
                Log.e(TAG, "Error removing package: $packageName", e)
                false
            }
        }
    }

    fun getBlockedPackages(ctx: Context): Set<String> {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val set = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
                // Return immutable copy
                HashSet(set)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting blocked packages", e)
                emptySet()
            }
        }
    }

    fun clearAllBlockedPackages(ctx: Context): Boolean {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().remove(KEY_PACKAGES).apply()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing blocked packages", e)
                false
            }
        }
    }

    // ===== Website blocking =====
    fun addBlockedWebsite(ctx: Context, website: String): Boolean {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val currentSet = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()
                val newSet = HashSet(currentSet)
                // Normalize: remove protocol and www
                val normalized = website.trim().lowercase()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .trimEnd('/')
                if (normalized.isEmpty()) return@synchronized false
                val added = newSet.add(normalized)
                prefs.edit().putStringSet(KEY_WEBSITES, newSet).apply()
                Log.d(TAG, "Added website: $normalized, success=$added")
                added
            } catch (e: Exception) {
                Log.e(TAG, "Error adding website: $website", e)
                false
            }
        }
    }

    fun removeBlockedWebsite(ctx: Context, website: String): Boolean {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val currentSet = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()
                val newSet = HashSet(currentSet)
                val removed = newSet.remove(website)
                prefs.edit().putStringSet(KEY_WEBSITES, newSet).apply()
                Log.d(TAG, "Removed website: $website, success=$removed")
                removed
            } catch (e: Exception) {
                Log.e(TAG, "Error removing website: $website", e)
                false
            }
        }
    }

    fun getBlockedWebsites(ctx: Context): Set<String> {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val set = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()
                // Return immutable copy
                HashSet(set)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting blocked websites", e)
                emptySet()
            }
        }
    }

    fun clearAllBlockedWebsites(ctx: Context): Boolean {
        return synchronized(lock) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().remove(KEY_WEBSITES).apply()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing blocked websites", e)
                false
            }
        }
    }
}
