package com.descope.sdk

/** The default base URL for the Descope API. */
const val DEFAULT_BASE_URL = "https://api.descope.com"

/**
 * The configuration of the Descope SDK.
 *
 * @property projectId the id of the Descope project.
 * @property baseUrl the base URL of the Descope server.
 */
data class DescopeConfig(
    val projectId: String,
    val baseUrl: String = DEFAULT_BASE_URL,
) {

    companion object {
        val initial = DescopeConfig("")
    }

}
