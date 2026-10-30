package tv.telegram.td

import org.drinkless.td.libcore.telegram.TdApi

sealed class TdResult {
    data class Ok(val value: TdApi.Object) : TdResult()
    data class TdError(val code: Int, val message: String) : TdResult()
    data class TransportError(val cause: Throwable) : TdResult()
    data object Timeout : TdResult()
}

inline fun <reified T : TdApi.Object> TdResult.valueOrNull(): T? =
    (this as? TdResult.Ok)?.value as? T
