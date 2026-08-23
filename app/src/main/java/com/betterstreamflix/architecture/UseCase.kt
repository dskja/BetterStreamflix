package com.betterstreamflix.architecture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Use case base — standardizes business logic execution with
 * input validation and result wrapping.
 */
abstract class UseCase<in Input, Output> {
    abstract suspend fun execute(input: Input): OperationResult<Output>

    /**
     * Safe execute — wraps in try-catch.
     */
    suspend fun safeExecute(input: Input): OperationResult<Output> {
        return try {
            execute(input)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            OperationResult.failure(e)
        }
    }

    operator fun invoke(input: Input): suspend () -> OperationResult<Output> = {
        safeExecute(input)
    }
}

/**
 * No-arg use case.
 */
abstract class NoArgUseCase<Output> {
    abstract suspend fun execute(): OperationResult<Output>

    suspend fun safeExecute(): OperationResult<Output> {
        return try {
            execute()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            OperationResult.failure(e)
        }
    }

    operator fun invoke(): suspend () -> OperationResult<Output> = {
        safeExecute()
    }
}

/**
 * Use case with validation.
 */
abstract class ValidatedUseCase<in Input, Output>(
    private val validator: (Input) -> ValidationResult,
) : UseCase<Input, Output>() {

    override suspend fun execute(input: Input): OperationResult<Output> {
        val validation = validator(input)
        if (validation is ValidationResult.Invalid) {
            return OperationResult.Failure(IllegalArgumentException(validation.message))
        }
        return executeValidated(input)
    }

    protected abstract suspend fun executeValidated(input: Input): OperationResult<Output>
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}
