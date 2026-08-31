package com.betterstreamflix.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkErrorMessageTest {

    @Test
    fun `HttpError 404 getUserMessage contains code`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val message = NetworkError.HttpError(404).getUserMessage(context)
        assertTrue(message.contains("404"))
    }

    @Test
    fun `Timeout getUserMessage is not empty`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val message = NetworkError.Timeout.getUserMessage(context)
        assertTrue(message.isNotEmpty())
    }
}
