package jp.co.soramitsu.common.validation

import jp.co.soramitsu.common.utils.requireException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface Validation<T, S> {

    suspend fun validate(value: T): ValidationStatus<S>
}

sealed class ValidationStatus<S> {

    class Valid<S> : ValidationStatus<S>()

    class NotValid<S>(val level: Level, val reason: S) : ValidationStatus<S>() {

        interface Level {
            val value: Int

            operator fun compareTo(other: Level): Int = value - other.value
        }
    }
}

enum class DefaultFailureLevel(override val value: Int) : ValidationStatus.NotValid.Level {
    WARNING(1), ERROR(2)
}

class CompositeValidation<T, S>(
    val validators: List<Validation<T, S>>
) : Validation<T, S> {

    override suspend fun validate(value: T): ValidationStatus<S> {
        val failureStatuses = validators.map { it.validate(value) }
            .filterIsInstance<ValidationStatus.NotValid<S>>()

        val mostSeriousReason = failureStatuses.maxByOrNull { it.level.value }

        return mostSeriousReason ?: ValidationStatus.Valid()
    }
}

class ValidationSystem<T, S>(
    private val validation: Validation<T, S>
) {

    suspend fun validate(
        value: T,
        ignoreUntil: ValidationStatus.NotValid.Level? = null
    ): Result<ValidationStatus<S>> = runCatching {
        withContext(Dispatchers.Default) {
            when (val status = validation.validate(value)) {
                is ValidationStatus.Valid -> status

                is ValidationStatus.NotValid -> {
                    if (ignoreUntil != null && status.level.value <= ignoreUntil.value) {
                        ValidationStatus.Valid()
                    } else {
                        status
                    }
                }
            }
        }
    }
}

suspend fun <S> ValidationSystem<Unit, S>.validate(
    ignoreUntil: ValidationStatus.NotValid.Level? = null
) = validate(Unit, ignoreUntil)

fun <S> Result<ValidationStatus<S>>.unwrap(
    onValid: () -> Unit,
    onInvalid: (ValidationStatus.NotValid<S>) -> Unit,
    onFailure: (Throwable) -> Unit
) {
    if (isSuccess) {
        when (val status = getOrThrow()) {
            is ValidationStatus.Valid<*> -> onValid()
            is ValidationStatus.NotValid<S> -> onInvalid(status)
        }
    } else {
        onFailure(requireException())
    }
}
