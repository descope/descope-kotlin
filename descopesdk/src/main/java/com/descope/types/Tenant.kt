package com.descope.types

import com.descope.sdk.DescopeAuth

/**
 * The [DescopeTenant] class represents a tenant in Descope.
 * 
 * You can retrieve the tenants for a user after authentication by calling [DescopeAuth.tenants].
 *
 * @property tenantId The unique identifier for the user in the project.
 * This is either an automatically generated value or a custom value that was set
 * when the tenant was created.
 * @property name The name of the tenant.
 * @property customAttributes A mapping of any custom attributes associated with this tenant. The custom attributes
 * are managed via the Descope console.
 */
data class DescopeTenant(
    val tenantId: String,
    val name: String,
    val customAttributes: Map<String, Any>,
)
