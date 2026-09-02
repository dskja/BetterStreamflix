package com.betterstreamflix

import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import com.betterstreamflix.activities.main.MainMobileActivity
import com.betterstreamflix.activities.main.MainTvActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = StreamFlixApp::class)
class MainActivityInflationTest {
    @Test
    fun mainActivity_launchesWithoutCrash() {
        val activityClass: Class<out FragmentActivity> =
            if (BuildConfig.APP_LAYOUT == "tv") {
                MainTvActivity::class.java
            } else {
                MainMobileActivity::class.java
            }

        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                assert(activity != null)
            }
        }
    }
}
