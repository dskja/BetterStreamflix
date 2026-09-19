package com.dskja.betterstreamflix.fragments.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsPreferenceSanitizerTest {

    @Test
    fun miscellaneousMobileXmlHasNoDependencies() {
        val xml = readResourceXml("settings_mobile.xml")
        assertFalse(
            "screen_more must not use android:dependency (BETTERSTREAMFLIX-K)",
            SettingsPreferenceSanitizer.miscellaneousXmlHasCrossScreenDependency(xml),
        )
    }

    @Test
    fun miscellaneousTvXmlHasNoDependencies() {
        val xml = readResourceXml("settings_tv.xml")
        assertFalse(
            "screen_more must not use android:dependency (BETTERSTREAMFLIX-K)",
            SettingsPreferenceSanitizer.miscellaneousXmlHasCrossScreenDependency(xml),
        )
    }

    @Test
    fun supportPreviewLivesOutsideMiscellaneousOnMobile() {
        val xml = readResourceXml("settings_mobile.xml")
        val previewAt = xml.indexOf("""android:key="p_settings_support_preview"""")
        val moreAt = xml.indexOf("""android:key="screen_more"""")
        assertTrue("support preview must exist", previewAt >= 0)
        assertTrue("screen_more must exist", moreAt >= 0)
        assertTrue(
            "support preview must not sit under Miscellaneous (screen_more)",
            previewAt < moreAt,
        )
        assertFalse(xml.contains("""android:dependency="EXPERIMENTAL_NEW_APP_DESIGN""""))
    }

    @Test
    fun detectorFlagsHistoricalCrashPattern() {
        val bad = """
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                <PreferenceScreen android:key="screen_more" android:title="More">
                    <Preference
                        android:key="p_settings_support_preview"
                        android:dependency="EXPERIMENTAL_NEW_APP_DESIGN" />
                </PreferenceScreen>
            </PreferenceScreen>
        """.trimIndent()
        assertTrue(SettingsPreferenceSanitizer.miscellaneousXmlHasCrossScreenDependency(bad))
    }

    private fun readResourceXml(name: String): String {
        val candidates = listOf(
            File("app/src/main/res/xml/$name"),
            File("../app/src/main/res/xml/$name"),
            File("../../app/src/main/res/xml/$name"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not locate $name (cwd=${File(".").absolutePath})")
        return file.readText()
    }
}
