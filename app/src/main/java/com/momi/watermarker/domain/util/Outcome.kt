package com.momi.watermarker.domain.util

/**
 * A minimal, explicit result type for operations that can fail.
 *
 * Preferred over throwing across layer boundaries so that callers are forced
 * to handle failure and so the domain stays free of framework exception types.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val data: T) : Outcome<T>
    data class Failure(val error: Throwable) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    companion object {
        /** Runs [block], wrapping a thrown exception into a [Failure]. */
        inline fun <T> catching(block: () -> T): Outcome<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(t)
        }
    }
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (Throwable) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}
