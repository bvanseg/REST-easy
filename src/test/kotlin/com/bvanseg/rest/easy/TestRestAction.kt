package com.bvanseg.rest.easy

import com.bvanseg.rest.easy.action.DefaultRestAction
import com.bvanseg.rest.easy.client.DefaultRestClient
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.bvanseg.rest.easy.response.RestResponse
import com.bvanseg.rest.easy.result.ResponseFailure
import com.bvanseg.rest.easy.result.ThrowableFailure
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Assert
import org.junit.Test
import java.net.http.HttpClient
import java.net.http.HttpResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * @author Boston Vanseghi
 */
class TestRestAction {

    private val httpClient = HttpClient.newBuilder().build()

    private val mapper = jacksonObjectMapper()

    private val jacksonBodyTransformer = object: BodyTransformer<String> {
        @Suppress("UNCHECKED_CAST")
        override fun <R : Any> read(response: RestResponse<String>, kclass: KClass<R>): R {
            return when {
                kclass.isSubclassOf(RestResponse::class) -> response as R
                kclass.isSubclassOf(String::class) -> response.body as R
                else -> mapper.readValue(response.body, kclass.java)
            }
        }
        override fun <R> write(input: R): String = mapper.writeValueAsString(input)
    }

    private val restClient = DefaultRestClient<String>(httpClient, HttpResponse.BodyHandlers.ofString(), jacksonBodyTransformer)

    private val endpoint = Endpoint("https://www.google.com")

    @Test
    fun testRestActionBlockOrNull() {
        // GIVEN
        val action = DefaultRestAction<String, String>(
            method = HttpMethod.GET,
            requestParameters = mapOf("foo" to "bar"),
            client = restClient
        ).onSuccess { str ->
            println(str)
        }.onFailure { failure ->
            when (failure) {
                is ThrowableFailure -> failure.throwable.printStackTrace()
                is ResponseFailure -> println(failure.response)
            }
        }.onResponse { response ->
            // Handle response manually...
        }

        // WHEN

        // THEN
        val result = action.blockOrNull(endpoint)

        Assert.assertTrue(result != null)
    }

    @Test
    fun testRestActionHeaderMerge() {
        // GIVEN
        val firstAction = DefaultRestAction<String, String>(
            method = HttpMethod.GET,
            client = restClient,
            headers = mapOf("foo" to "bar")
        )
        val secondAction = DefaultRestAction<String, String>(
            method = HttpMethod.GET,
            client = restClient,
            headers = mapOf("foo" to "foobar")
        )

        // WHEN
        val thirdAction = firstAction.merge(secondAction)

        // THEN
        Assert.assertTrue(thirdAction.headers.containsKey("foo"))
        Assert.assertEquals("foobar", thirdAction.headers["foo"])
    }
}