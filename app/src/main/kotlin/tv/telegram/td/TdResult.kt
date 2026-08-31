package tv.telegram.td

import org.drinkless.td.libcore.telegram.TdApi

/**
 * Result of a blocking [TdClient.execute] call, distinguishing the four
 * distinct failure modes that were previously all collapsed into `null`:
 * TDLib returned a [TdApi.Error], the call timed out, the transport threw,
 * or the client was not started. Callers can now branch precisely instead of
 * guessing (e.g. treating every `null` as a timeout).
 */
sealed class TdResult {
    data class Ok(val value: TdApi.Object) : TdResult()
    data class TdError(val code: Int, val message: String) : TdResult()
    data class TransportError(val cause: Throwable) : TdResult()
    data object Timeout : TdResult()
}

/**
 * Convenience for the common "I only care about a successful typed value"
 * pattern. Returns the wrapped value if this is an [TdResult.Ok] holding an
 * instance of [T], otherwise `null`.
 */
inline fun <reified T : TdApi.Object> TdResult.valueOrNull(): T? =
    (this as? TdResult.Ok)?.value as? T
