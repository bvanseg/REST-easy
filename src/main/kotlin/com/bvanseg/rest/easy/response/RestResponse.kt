package com.bvanseg.rest.easy.response

/**
 * @author Boston Vanseghi
 */
data class RestResponse<T>(
    val body: T,
    val statusCode: Int,
    val sourceResponse: Any
)