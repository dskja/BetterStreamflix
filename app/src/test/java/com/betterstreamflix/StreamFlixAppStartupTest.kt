package com.betterstreamflix

import androidx.test.core.app.ApplicationProvider
import com.betterstreamflix.download.Media3OfflineDownloads
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = StreamFlixApp::class)
class StreamFlixAppStartupTest {

    @Test
    fun `application onCreate completes without crashing`() {
        val app = ApplicationProvider.getApplicationContext<StreamFlixApp>()
        assertNotNull(app)
        assertNotNull(StreamFlixApp.instance)
    }

    @Test
    fun `media3 init survives repeated cold start`() {
        val app = ApplicationProvider.getApplicationContext<StreamFlixApp>()
        Media3OfflineDownloads.init(app)
        Media3OfflineDownloads.init(app)
        // Second init should be a no-op, not a crash.
    }
}
