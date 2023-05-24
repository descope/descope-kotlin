package com.descope.types

sealed class Result<out T> {
    data class Success<T>(val result: T) : Result<T>()
    data class Failure(val error: Exception) : Result<Nothing>()
}
