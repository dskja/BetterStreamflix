package com.betterstreamflix.fragments.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.betterstreamflix.StreamFlixApp
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.providers.SerienStreamProvider
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = StreamFlixApp::class)
class HomeViewModelStartupTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        UserPreferences.currentProvider = SerienStreamProvider
        SerienStreamProvider.initialize(context)
        AppDatabase.setup(context)
    }

    @Test
    fun `home state flow does not crash for SerienStream provider`() = runBlocking {
        val viewModel = HomeViewModel(AppDatabase.getInstance(context))
        val state = withTimeout(8_000) {
            viewModel.state.first { it !is HomeViewModel.State.Loading }
        }
        assertTrue(
            state is HomeViewModel.State.SuccessLoading || state is HomeViewModel.State.FailedLoading,
        )
    }
}
