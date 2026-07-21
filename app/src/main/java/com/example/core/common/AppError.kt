package com.example.core.common

import androidx.annotation.Keep

@Keep
data class AppError(
    val step: Int, // 1 to 7 corresponding to apply poster flow
    val title: String,
    override val message: String,
    val serverCode: String? = null,
    val httpStatus: Int? = null,
    val retryable: Boolean = true,
    val sanitizedDetails: String = ""
) : Exception(message)
