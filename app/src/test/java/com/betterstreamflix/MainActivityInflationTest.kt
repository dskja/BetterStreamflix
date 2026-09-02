
package com.betterstreamflix

import androidx.test.core.app.ActivityScenario
import com.betterstreamflix.activities.main.MainMobileActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = StreamFlixApp::class)
class MainActivityInflationTest {
    @Test
    fun mainMobileActivity_launchesWithoutCrash() {
        ActivityScenario.launch(MainMobileActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assert(activity != null)
            }
        }
    }
}
