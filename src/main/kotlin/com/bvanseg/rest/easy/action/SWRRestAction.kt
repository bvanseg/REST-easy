package com.bvanseg.rest.easy.action

import arrow.core.Either
import com.bvanseg.rest.easy.HttpMethod
import com.bvanseg.rest.easy.client.RestClient
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.bvanseg.rest.easy.result.RestActionFailure
import com.bvanseg.rest.easy.result.ThrowableFailure
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
class SWRRestAction<I, O: Any>(
    override val method: HttpMethod = HttpMethod.GET,
    override val client: RestClient<I>,
    override val body: Any? = null,
    override val requestParameters: Map<String, String> = emptyMap(),
    override val headers: Map<String, String> = emptyMap(),
    private val refreshIntervalMillis: Long = 0L,
    override val kClass: KClass<O>
): DefaultRestAction<I, O>(method, client, body, requestParameters, headers, kClass) {

    companion object {
        inline operator fun <I, reified O: Any> invoke(
            method: HttpMethod = HttpMethod.GET,
            client: RestClient<I>,
            body: Any? = null,
            requestParameters: Map<String, String> = emptyMap(),
            headers: Map<String, String> = emptyMap(),
            refreshIntervalMillis: Long = 0L,
        ): SWRRestAction<I, O> = SWRRestAction(
            method, client, body, requestParameters, headers, refreshIntervalMillis, O::class
        )
    }

    @Volatile
    var data: O? = null
        set(value) {
            lastUpdateTimeMillis = System.currentTimeMillis()
            field = value
        }

    @Volatile
    private var lastUpdateTimeMillis = System.currentTimeMillis()

    private val nextUpdateTimeMillis: Long
        get() = lastUpdateTimeMillis + refreshIntervalMillis

    private val shouldUpdate: Boolean
        get() = refreshIntervalMillis > 0 && System.currentTimeMillis() >= nextUpdateTimeMillis

    private val hasData: Boolean
        get() = data != null

    override fun onDataTransformed(data: O) {
        this.data = data
        super.onDataTransformed(data)
    }

    override fun block(endpoint: Endpoint): Either<RestActionFailure, O> {
        return if (hasData && !shouldUpdate) {
            try {
                val successObject = data!!
                successCallbackDeque.forEach { it.invoke(successObject) }
                Either.Right(successObject)
            } catch (e: Exception) {
                val failure = ThrowableFailure(e)
                failureCallbackDeque.forEach { it.invoke(failure) }
                Either.Left(failure)
            }
        } else {
            super.block(endpoint)
        }
    }

    override fun async(endpoint: Endpoint, callback: (Either<RestActionFailure, O>) -> Unit) {
        if (hasData && !shouldUpdate) {
            try {
                val successObject = data!!
                successCallbackDeque.forEach { it.invoke(successObject) }
                callback(Either.Right(successObject))
            } catch (e: Exception) {
                val failure = ThrowableFailure(e)
                failureCallbackDeque.forEach { it.invoke(failure) }
                callback(Either.Left(failure))
            }
        } else {
            super.async(endpoint, callback)
        }
    }

    fun flush() {
        data = null
    }
}