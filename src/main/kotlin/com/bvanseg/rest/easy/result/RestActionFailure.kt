package com.bvanseg.rest.easy.result

import java.net.http.HttpResponse

/**
 * @author Boston Vanseghi
 */
abstract class RestActionFailure

/**
 * @author Boston Vanseghi
 */
class ResponseFailure(
    val response: HttpResponse<*>
) : RestActionFailure()

/**
 * @author Boston Vanseghi
 */
class ThrowableFailure(
    val throwable: Throwable,
    val response: HttpResponse<*>? = null
) : RestActionFailure()
