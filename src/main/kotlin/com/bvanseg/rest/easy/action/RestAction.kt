package com.bvanseg.rest.easy.action

import arrow.core.Either
import com.bvanseg.rest.easy.HttpMethod
import com.bvanseg.rest.easy.client.RestClient
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.bvanseg.rest.easy.response.RestResponse
import com.bvanseg.rest.easy.result.RestActionFailure
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
interface RestAction<O: Any> {
    val method: HttpMethod
    val body: Any?
    val requestParameters: Map<String, String>
    val headers: Map<String, String>
    val client: RestClient<*>
    val kClass: KClass<O>

    fun block(endpoint: Endpoint): Either<RestActionFailure, O>
    fun async(endpoint: Endpoint, callback: (Either<RestActionFailure, O>) -> Unit = {})
    fun asyncFuture(endpoint: Endpoint): CompletableFuture<out RestResponse<*>>

    fun merge(other: RestAction<*>): RestAction<O> {
        return DefaultRestAction(
            method = other.method,
            client = other.client,
            body = body,
            headers = this.headers + other.headers,
            requestParameters = this.requestParameters + other.requestParameters,
            kClass = kClass
        )
    }
}