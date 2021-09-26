package com.bvanseg.rest.easy.client

import com.bvanseg.rest.easy.BodyTransformer
import com.bvanseg.rest.easy.request.RestRequest
import com.bvanseg.rest.easy.response.RestResponse
import java.util.concurrent.CompletableFuture

/**
 * @author Boston Vanseghi
 */
abstract class RestClient<I>(val bodyTransformer: BodyTransformer<I>) {
    abstract fun async(request: RestRequest): CompletableFuture<RestResponse<I>>
    abstract fun block(request: RestRequest): RestResponse<I>
}