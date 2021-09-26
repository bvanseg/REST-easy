package com.bvanseg.rest.easy

import com.bvanseg.rest.easy.response.RestResponse
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
interface BodyTransformer<I> {
    fun <O: Any> read(response: RestResponse<I>, kclass: KClass<O>): O
    fun <O> write(input: O): String
}