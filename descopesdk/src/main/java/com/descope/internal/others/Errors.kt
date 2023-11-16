package com.descope.internal.others

import com.descope.types.DescopeException

internal fun DescopeException.with(desc: String? = null, message: String? = null, cause: Throwable? = null) = DescopeException(
    code = code,
    desc = desc ?: this.desc,
    message = message ?: this.message,
    cause = cause ?: this.cause,
)
