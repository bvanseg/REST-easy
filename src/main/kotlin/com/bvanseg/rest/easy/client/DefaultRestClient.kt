package com.bvanseg.rest.easy.client

import com.bvanseg.rest.easy.BodyTransformer
import com.bvanseg.rest.easy.request.RestRequest
import com.bvanseg.rest.easy.response.RestResponse
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

/**
 * @author Boston Vanseghi
 */
class DefaultRestClient<I>(
    val httpClient: HttpClient = HttpClient.newHttpClient(),
    val bodyHandler: HttpResponse.BodyHandler<I>,
    bodyTransformer: BodyTransformer<I>,
): RestClient<I>(bodyTransformer) {

    override fun block(request: RestRequest): RestResponse<I> {
        val httpRequest = toHttpRequest(request)
        val httpResponse = httpClient.send(httpRequest, bodyHandler)
        return RestResponse(httpResponse.body(), httpResponse.statusCode(), httpResponse)
    }

    override fun async(request: RestRequest): CompletableFuture<RestResponse<I>> {
        val httpRequest = toHttpRequest(request)
        return httpClient.sendAsync(httpRequest, bodyHandler).thenApplyAsync { httpResponse ->
            return@thenApplyAsync RestResponse(httpResponse.body(), httpResponse.statusCode(), httpResponse)
        }
    }

    private fun toHttpRequest(request: RestRequest): HttpRequest {
        val builder = HttpRequest.newBuilder(request.target)
            .method(
                request.method.name, if (request.body == null) {
                    HttpRequest.BodyPublishers.noBody()
                } else {
                    HttpRequest.BodyPublishers.ofString(bodyTransformer.write(request.body))
                }
            )

        request.headers.forEach { entry -> builder.header(entry.key, entry.value) }

        return builder.build()
    }
}