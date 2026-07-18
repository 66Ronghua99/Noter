package com.cory.noter.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

enum class NoterApiFailureCode {
    UNAUTHORIZED("unauthorized"),
    INVALID_REQUEST("invalid_request"),
    PAYLOAD_TOO_LARGE("payload_too_large"),
    RATE_LIMITED("rate_limited"),
    UPSTREAM_REJECTED("upstream_rejected"),
    UPSTREAM_UNAVAILABLE("upstream_unavailable"),
    UPSTREAM_TIMEOUT("upstream_timeout"),
    INVALID_UPSTREAM_RESPONSE("invalid_upstream_response"),
    SERVICE_UNAVAILABLE("service_unavailable"),
    INVALID_SERVICE_RESPONSE("invalid_service_response"),
    NETWORK_UNAVAILABLE("network_unavailable"),
    ;

    val wireValue: String

    constructor(wireValue: String) {
        this.wireValue = wireValue
    }

    companion object {
        fun fromWireValue(value: String): NoterApiFailureCode =
            entries.firstOrNull { it.wireValue == value } ?: INVALID_SERVICE_RESPONSE
    }
}

data class NoterApiFailure(
    val code: NoterApiFailureCode,
    val retryable: Boolean,
    val retryAfterSeconds: Long? = null,
)

internal fun parseNoterApiFailure(
    statusCode: Int,
    responseBody: String,
    retryAfterHeader: String?,
    json: Json,
): NoterApiFailure {
    val envelope = runCatching { json.parseToJsonElement(responseBody).jsonObject }.getOrNull()
    val error = envelope?.get("error") as? JsonObject
    val code = (error?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?.let(NoterApiFailureCode::fromWireValue)
        ?: fallbackCode(statusCode)
    val retryable = (error?.get("retryable") as? JsonPrimitive)?.booleanOrNull
        ?: fallbackRetryable(statusCode, code)
    return NoterApiFailure(
        code = code,
        retryable = retryable,
        retryAfterSeconds = retryAfterHeader?.toLongOrNull()?.takeIf { it > 0 },
    )
}

private fun fallbackCode(statusCode: Int): NoterApiFailureCode = when (statusCode) {
    401, 403 -> NoterApiFailureCode.UNAUTHORIZED
    413 -> NoterApiFailureCode.PAYLOAD_TOO_LARGE
    422 -> NoterApiFailureCode.INVALID_REQUEST
    429 -> NoterApiFailureCode.RATE_LIMITED
    502 -> NoterApiFailureCode.UPSTREAM_REJECTED
    504 -> NoterApiFailureCode.UPSTREAM_TIMEOUT
    else -> NoterApiFailureCode.SERVICE_UNAVAILABLE
}

private fun fallbackRetryable(statusCode: Int, code: NoterApiFailureCode): Boolean =
    code == NoterApiFailureCode.RATE_LIMITED || statusCode == 502 || statusCode == 503 || statusCode == 504
