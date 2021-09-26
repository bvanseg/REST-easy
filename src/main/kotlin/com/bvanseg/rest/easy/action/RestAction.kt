package com.bvanseg.rest.easy.action

import arrow.core.Either
import com.bvanseg.rest.easy.HttpMethod
import com.bvanseg.rest.easy.client.RestClient
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.bvanseg.rest.easy.result.RestActionFailure
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
interface RestAction<T: Any> {
    val method: HttpMethod
    val requestParameters: Map<String, String>
    val headers: Map<String, String>
    val client: RestClient
    val kClass: KClass<T>

    fun block(endpoint: Endpoint): Either<RestActionFailure, T>
    fun async(endpoint: Endpoint, callback: (Either<RestActionFailure, T>) -> Unit = {})
    fun asyncFuture(endpoint: Endpoint): CompletableFuture<HttpResponse<String>>

    fun merge(other: RestAction<*>): RestAction<T> {
        return DefaultRestAction(
            method = other.method,
            client = other.client,
            headers = this.headers + other.headers,
            requestParameters = this.requestParameters + other.requestParameters,
            kClass = kClass
        )
    }

    fun toHttpRequest(url: String, body: T? = null): HttpRequest
}