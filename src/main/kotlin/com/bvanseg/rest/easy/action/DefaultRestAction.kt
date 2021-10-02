package com.bvanseg.rest.easy.action

import arrow.core.Either
import arrow.core.getOrHandle
import com.bvanseg.rest.easy.HttpMethod
import com.bvanseg.rest.easy.client.RestClient
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.bvanseg.rest.easy.request.RestRequest
import com.bvanseg.rest.easy.response.RestResponse
import com.bvanseg.rest.easy.result.ResponseFailure
import com.bvanseg.rest.easy.result.RestActionFailure
import com.bvanseg.rest.easy.result.ThrowableFailure
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
open class DefaultRestAction<I, O: Any>(
    override val method: HttpMethod = HttpMethod.GET,
    override val client: RestClient<I>,
    override val body: Any? = null,
    override val requestParameters: Map<String, String> = emptyMap(),
    override val headers: Map<String, String> = emptyMap(),
    override val kClass: KClass<O>
): RestAction<O> {

    companion object {
        inline operator fun <I, reified O: Any> invoke(
            method: HttpMethod = HttpMethod.GET,
            client: RestClient<I>,
            body: Any? = null,
            requestParameters: Map<String, String> = emptyMap(),
            headers: Map<String, String> = emptyMap(),
        ): DefaultRestAction<I, O> = DefaultRestAction(
            method = method,
            client = client,
            body = body,
            requestParameters = requestParameters,
            headers = headers,
            kClass = O::class
        )

        inline operator fun <I, reified O: Any> invoke(
            client: RestClient<I>,
            action: RestAction<*>
        ): DefaultRestAction<I, O> = DefaultRestAction(
            method = action.method,
            client = client,
            body = action.body,
            requestParameters = action.requestParameters,
            headers = action.headers,
            kClass = O::class
        )
    }

    constructor(
        client: RestClient<I>,
        restRequest: RestRequest,
        kClass: KClass<O>
    ): this(
        method = restRequest.method,
        client = client,
        body = restRequest.body,
        requestParameters = restRequest.requestParameters,
        headers = restRequest.headers,
        kClass = kClass
    )

    constructor(
        client: RestClient<I>,
        action: RestAction<O>
    ): this(
        method = action.method,
        client = client,
        body = action.body,
        requestParameters = action.requestParameters,
        headers = action.headers,
        kClass = action.kClass
    )

    private val requestQuery: String
        get() = "?" + requestParameters.entries.joinToString("&")

    protected val eitherCallbackDeque by lazy { ConcurrentLinkedDeque<(Either<RestActionFailure, O>) -> Unit>() }
    protected val failureCallbackDeque by lazy { ConcurrentLinkedDeque<(RestActionFailure) -> Unit>() }
    protected val responseCallbackDeque by lazy { ConcurrentLinkedDeque<(RestResponse<*>) -> Unit>() }
    protected val successCallbackDeque by lazy { ConcurrentLinkedDeque<(O) -> Unit>() }

    protected val isRequestSending = AtomicBoolean(false)

    fun onFailure(callback: (RestActionFailure) -> Unit): DefaultRestAction<I, O> = this.apply {
        failureCallbackDeque.add(callback)
    }

    fun onSuccess(callback: (O) -> Unit): DefaultRestAction<I, O> = this.apply {
        successCallbackDeque.add(callback)
    }

    protected open fun onResponse(response: RestResponse<*>) = Unit

    protected open fun onDataTransformed(data: O) = Unit

    fun onResponse(callback: (RestResponse<*>) -> Unit): DefaultRestAction<I, O> = this.apply {
        responseCallbackDeque.add(callback)
    }

    override fun block(endpoint: Endpoint): Either<RestActionFailure, O> {
        val response = client.block(buildRestRequest(endpoint.url + requestQuery))

        onResponse(response)
        responseCallbackDeque.forEach { it.invoke(response) }

        if (response.statusCode in 400..599) {
            val failure = ResponseFailure(response)
            failureCallbackDeque.forEach { it.invoke(failure) }
            return Either.Left(failure)
        }

        val content: O = try {
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

    fun blockOrNull(endpoint: Endpoint): O? = block(endpoint).orNull()
    fun blockOrDefault(endpoint: Endpoint, defaultValue: O): O = blockOrNull(endpoint) ?: defaultValue
    fun blockOrHandle(endpoint: Endpoint, callback: (RestActionFailure) -> Unit): O? = block(endpoint).getOrHandle {
        callback(it)
        null
    }

    private fun runAndClearEitherCallbacks(either: Either<RestActionFailure, O>) {
        eitherCallbackDeque.forEach { it.invoke(either) }
        eitherCallbackDeque.clear()
        isRequestSending.getAndSet(false)
    }

    override fun async(endpoint: Endpoint, callback: (Either<RestActionFailure, O>) -> Unit) {
        eitherCallbackDeque.add(callback)

        if (isRequestSending.get()) {
            return
        }

        isRequestSending.getAndSet(true)

        client.async(buildRestRequest(endpoint.url + requestQuery)).whenComplete { response, throwable ->
            try {
                onResponse(response)
                responseCallbackDeque.forEach { it.invoke(response) }

                throwable?.let { e ->
                    val failure = ThrowableFailure(e, response)
                    failureCallbackDeque.forEach { it.invoke(failure) }
                    return@whenComplete runAndClearEitherCallbacks(Either.Left(failure))
                }

                if (response.statusCode in 400..599) {
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

    override fun asyncFuture(endpoint: Endpoint): CompletableFuture<RestResponse<I>> {
        return client.async(buildRestRequest(endpoint.url + requestQuery))
    }

    private fun buildRestRequest(url: String): RestRequest = RestRequest(
        target = URI.create(url),
        body = body,
        method = method,
        headers = headers,
        requestParameters = requestParameters
    )
}