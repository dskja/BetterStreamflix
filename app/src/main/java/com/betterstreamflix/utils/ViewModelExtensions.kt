package com.betterstreamflix.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Extension functions for safe coroutine launching in ViewModels.
 * Automatically wraps launches with GlobalErrorHandler to prevent
 * unhandled exceptions from silently crashing.
 */

/**
 * Launch a coroutine in viewModelScope with global error handling.
 * Use this instead of viewModelScope.launch when you don't have
 * your own try-catch.
 */
fun ViewModel.launchSafe(
    dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewModelScope.launch(dispatcher + GlobalErrorHandler.handler, block = block)
}

/**
 * Launch a coroutine in viewModelScope on IO dispatcher with error handling.
 */
fun ViewModel.launchIO(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewModelScope.launch(Dispatchers.IO + GlobalErrorHandler.handler, block = block)
}
