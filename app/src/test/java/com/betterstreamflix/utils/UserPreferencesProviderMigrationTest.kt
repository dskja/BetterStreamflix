package com.betterstreamflix.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.betterstreamflix.providers.SerienStreamProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesProviderMigrationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        UserPreferences.setup(context)
        UserPreferences.currentProvider = null
    }

    @Test
    fun `legacy serienstream provider name resolves to SerienStreamProvider`() {
        context.getSharedPreferences(
            "${com.betterstreamflix.BuildConfig.APPLICATION_ID}.preferences",
            Context.MODE_PRIVATE,
        ).edit()
            .putString("CURRENT_PROVIDER", "serienstream")
            .commit()

        UserPreferences.setup(context)

        assertEquals(SerienStreamProvider, UserPreferences.currentProvider)
    }
}
