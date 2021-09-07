package com.bvanseg.rest.easy

import com.bvanseg.rest.easy.action.DefaultRestAction
import com.bvanseg.rest.easy.endpoint.Endpoint
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.http.HttpClient
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import org.junit.Assert
import org.junit.Test
import java.net.http.HttpResponse

/**
 * @author Boston Vanseghi
 */
class TestRestAction {

    private val httpClient = HttpClient.newBuilder().build()

    private val mapper = jacksonObjectMapper()

    private val jacksonBodyTransformer = object: BodyTransformer {
        @Suppress("UNCHECKED_CAST")
        override fun <R : Any> read(response: HttpResponse<String>, kclass: KClass<R>): R {
            return when {
                kclass.isSubclassOf(HttpResponse::class) -> response as R
                kclass.isSubclassOf(String::class) -> response.body() as R
                else -> mapper.readValue(response.body(), kclass.java)
            }
        }
        override fun <R> write(input: R): String = mapper.writeValueAsString(input)
    }

    private val endpoint = Endpoint("https://www.google.com", httpClient)

    @Test
    fun testRestActionBlockOrNull() {
        // GIVEN
        val action = DefaultRestAction<String>(
            method = HttpMethod.GET,
            requestParameters = mapOf("foo" to "bar"),
            transformer = jacksonBodyTransformer
        )

        // WHEN

        // THEN
        val result = action.blockOrNull(endpoint)

        Assert.assertTrue(result != null)
    }

    @Test
    fun testRestActionHeaderMerge() {
        // GIVEN
        val firstAction = DefaultRestAction<String>(
            method = HttpMethod.GET,
            transformer = jacksonBodyTransformer,
            headers = mapOf("foo" to "bar")
        )
        val secondAction = DefaultRestAction<String>(
            method = HttpMethod.GET,
            transformer = jacksonBodyTransformer,
            headers = mapOf("foo" to "foobar")
        )

        // WHEN
        val thirdAction = firstAction.merge(secondAction)

        // THEN
        Assert.assertTrue(thirdAction.headers.containsKey("foo"))
        Assert.assertEquals("foobar", thirdAction.headers["foo"])
    }
}