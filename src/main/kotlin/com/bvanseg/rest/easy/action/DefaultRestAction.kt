package com.bvanseg.rest.easy.action

import arrow.core.Either
import arrow.core.getOrHandle
import com.bvanseg.rest.easy.HttpMethod
import com.bvanseg.rest.easy.client.RestClient
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.bvanseg.rest.easy.result.ResponseFailure
import com.bvanseg.rest.easy.result.RestActionFailure
import com.bvanseg.rest.easy.result.ThrowableFailure
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
open class DefaultRestAction<T: Any>(
    override val method: HttpMethod = HttpMethod.GET,
    override val client: RestClient,
    override val requestParameters: Map<String, String> = emptyMap(),
    override val headers: Map<String, String> = emptyMap(),
    override val kClass: KClass<T>
): RestAction<T> {

    companion object {
        inline operator fun <reified T: Any> invoke(
            method: HttpMethod = HttpMethod.GET,
            client: RestClient,
            requestParameters: Map<String, String> = emptyMap(),
            headers: Map<String, String> = emptyMap(),
        ): DefaultRestAction<T> = DefaultRestAction(
            method, client, requestParameters, headers, T::class
        )

        inline operator fun <reified T: Any> invoke(
            action: RestAction<*>
        ): DefaultRestAction<T> = DefaultRestAction(
            action.method, action.client, action.requestParameters, action.headers, T::class
        )
    }

    constructor(action: RestAction<T>):
            this(action.method, action.client, action.requestParameters, action.headers, action.kClass)

    private val requestQuery: String
        get() = "?" + requestParameters.entries.joinToString("&")

    protected val eitherCallbackDeque by lazy { ConcurrentLinkedDeque<(Either<RestActionFailure, T>) -> Unit>() }
    protected val failureCallbackDeque by lazy { ConcurrentLinkedDeque<(RestActionFailure) -> Unit>() }
    protected val responseCallbackDeque by lazy { ConcurrentLinkedDeque<(HttpResponse<String>) -> Unit>() }
    protected val successCallbackDeque by lazy { ConcurrentLinkedDeque<(T) -> Unit>() }

    protected val isRequestSending = AtomicBoolean(false)

    fun onFailure(callback: (RestActionFailure) -> Unit): DefaultRestAction<T> = this.apply {
        failureCallbackDeque.add(callback)
    }

    fun onSuccess(callback: (T) -> Unit): DefaultRestAction<T> = this.apply {
        successCallbackDeque.add(callback)
    }

    protected open fun onResponse(response: HttpResponse<String>) = Unit

    protected open fun onDataTransformed(data: T) = Unit

    fun onResponse(callback: (HttpResponse<String>) -> Unit): DefaultRestAction<T> = this.apply {
        responseCallbackDeque.add(callback)
    }

    override fun block(endpoint: Endpoint): Either<RestActionFailure, T> {
        val response = client.httpClient.send(toHttpRequest(endpoint.url + requestQuery), HttpResponse.BodyHandlers.ofString())

        onResponse(response)
        responseCallbackDeque.forEach { it.invoke(response) }

        if (response.statusCode() in 400..599) {
            val failure = ResponseFailure(response)
            failureCallbackDeque.forEach { it.invoke(failure) }
            return Either.Left(failure)
        }

        val content: T = try {
            val successObject = client.bodyTransformer.read(response, kClass)
            onDataTransformed(successObject)
            successCallbackDeque.forEach { it.invoke(successObject) }
            successObject
        } catch(e: Exception) {
            val failure = ThrowableFailure(e, response)
            failureCallbackDeque.forEach { it.invoke(failure) }
            return Either.Left(failure)
        }

        return Either.Right(content)
    }

    fun blockOrNull(endpoint: Endpoint): T? = block(endpoint).orNull()
    fun blockOrDefault(endpoint: Endpoint, defaultValue: T): T = blockOrNull(endpoint) ?: defaultValue
    fun blockOrHandle(endpoint: Endpoint, callback: (RestActionFailure) -> Unit): T? = block(endpoint).getOrHandle {
        callback(it)
        null
    }

    private fun runAndClearEitherCallbacks(either: Either<RestActionFailure, T>) {
        eitherCallbackDeque.forEach { it.invoke(either) }
        eitherCallbackDeque.clear()
        isRequestSending.getAndSet(false)
    }

    override fun async(endpoint: Endpoint, callback: (Either<RestActionFailure, T>) -> Unit) {
        eitherCallbackDeque.add(callback)

        if (isRequestSending.get()) {
            return
        }

        isRequestSending.getAndSet(true)

        client.httpClient.sendAsync(toHttpRequest(endpoint.url + requestQuery), HttpResponse.BodyHandlers.ofString()
        ).whenComplete { response, throwable ->
            try {
                onResponse(response)
                responseCallbackDeque.forEach { it.invoke(response) }

                throwable?.let { e ->
                    val failure = ThrowableFailure(e, response)
                    failureCallbackDeque.forEach { it.invoke(failure) }
                    return@whenComplete runAndClearEitherCallbacks(Either.Left(failure))
                }

                if (response.statusCode() in 400..599) {
                    val failure = ResponseFailure(response)
                    failureCallbackDeque.forEach { it.invoke(failure) }
                    return@whenComplete runAndClearEitherCallbacks(Either.Left(failure))
                }

                val successObject = client.bodyTransformer.read(response, kClass)
                onDataTransformed(successObject)
                successCallbackDeque.forEach { it.invoke(successObject) }
                return@whenComplete runAndClearEitherCallbacks(Either.Right(successObject))
            } catch (e: Exception) {
                val failure = ThrowableFailure(e, response)
                failureCallbackDeque.forEach { it.invoke(failure) }
                runAndClearEitherCallbacks(Either.Left(failure))
            }
        }
    }

    override fun asyncFuture(endpoint: Endpoint): CompletableFuture<HttpResponse<String>> {
        return client.httpClient.sendAsync(toHttpRequest(endpoint.url + requestQuery), HttpResponse.BodyHandlers.ofString())
    }

    override fun toHttpRequest(url: String, body: T?): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(url + requestQuery))
            .method(
                method.name, if (body == null) {
                    HttpRequest.BodyPublishers.noBody()
                } else {
                    HttpRequest.BodyPublishers.ofString(client.bodyTransformer.write(body))
                }
            )

        headers.forEach { entry -> builder.header(entry.key, entry.value) }

        return builder.build()
    }
}