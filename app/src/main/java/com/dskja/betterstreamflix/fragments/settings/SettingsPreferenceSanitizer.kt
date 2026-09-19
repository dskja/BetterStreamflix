package com.dskja.betterstreamflix.fragments.settings

import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceGroup

/**
 * Nested PreferenceScreens (e.g. Settings → Miscellaneous) only expose their own
 * subtree to [androidx.preference.PreferenceManager]. Cross-screen
 * `android:dependency` targets are then missing and crash with
 * IllegalStateException (Sentry BETTERSTREAMFLIX-K).
 *
 * Call after [androidx.preference.PreferenceFragmentCompat.setPreferencesFromResource]
 * and before preferences bind/attach.
 */
object SettingsPreferenceSanitizer {
    private const val TAG = "SettingsPrefSanitize"

    fun clearBrokenDependencies(root: PreferenceGroup?): Int {
        if (root == null) return 0
        val keys = LinkedHashSet<String>()
        collectKeys(root, keys)
        return sanitize(root, keys)
    }

    /** Exposed for unit tests — keys present in a preference subtree. */
    fun collectKeysForTest(root: PreferenceGroup): Set<String> {
        val keys = LinkedHashSet<String>()
        collectKeys(root, keys)
        return keys
    }

    private fun collectKeys(group: PreferenceGroup, out: MutableSet<String>) {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            preference.key?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            if (preference is PreferenceGroup) {
                collectKeys(preference, out)
            }
        }
    }

    private fun sanitize(group: PreferenceGroup, keys: Set<String>): Int {
        var cleared = 0
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            val dependency = preference.dependency
            if (!dependency.isNullOrBlank() && dependency !in keys) {
                Log.w(
                    TAG,
                    "Clearing broken dependency \"$dependency\" on preference \"${preference.key}\" " +
                        "(title=${preference.title}) — not in current PreferenceScreen hierarchy",
                )
                preference.dependency = null
                cleared++
            }
            if (preference is PreferenceGroup) {
                cleared += sanitize(preference, keys)
            }
        }
        return cleared
    }

    /**
     * Lightweight XML audit used by unit tests — detects `android:dependency`
     * under `screen_more` (Miscellaneous), which historically crashed.
     */
    fun miscellaneousXmlHasCrossScreenDependency(settingsXml: String): Boolean {
        val screenMoreStart = settingsXml.indexOf("""android:key="screen_more"""")
        if (screenMoreStart < 0) return false
        // Prefer the PreferenceScreen that owns screen_more (search backwards for tag open).
        val screenOpen = settingsXml.lastIndexOf("<PreferenceScreen", screenMoreStart)
        if (screenOpen < 0) return false
        var depth = 0
        var i = screenOpen
        while (i < settingsXml.length) {
            val nextOpen = settingsXml.indexOf("<PreferenceScreen", i + 1)
            val nextClose = settingsXml.indexOf("</PreferenceScreen>", i + 1)
            if (nextClose < 0) break
            if (nextOpen in 1 until nextClose) {
                depth++
                i = nextOpen
                continue
            }
            if (depth == 0) {
                val block = settingsXml.substring(screenOpen, nextClose)
                return block.contains("android:dependency", ignoreCase = true)
            }
            depth--
            i = nextClose
        }
        return false
    }
}
