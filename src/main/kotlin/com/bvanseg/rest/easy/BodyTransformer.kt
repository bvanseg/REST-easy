package com.bvanseg.rest.easy

import java.net.http.HttpResponse
import kotlin.reflect.KClass

/**
 * @author Boston Vanseghi
 */
interface BodyTransformer {
    fun <R: Any> read(response: HttpResponse<String>, kclass: KClass<R>): R
    fun <R> write(input: R): String
}