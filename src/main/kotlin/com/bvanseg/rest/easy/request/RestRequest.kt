package com.bvanseg.rest.easy.request

import com.bvanseg.rest.easy.HttpMethod
import java.net.URI

/**
 * @author Boston Vanseghi
 */
data class RestRequest(
    val target: URI,
    val body: Any? = null,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val requestParameters: Map<String, String> = emptyMap()
)