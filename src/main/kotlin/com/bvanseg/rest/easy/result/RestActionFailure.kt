package com.bvanseg.rest.easy.result

import com.bvanseg.rest.easy.response.RestResponse

/**
 * @author Boston Vanseghi
 */
abstract class RestActionFailure

/**
 * @author Boston Vanseghi
 */
class ResponseFailure(
    val response: RestResponse<*>
) : RestActionFailure()

/**
 * @author Boston Vanseghi
 */
class ThrowableFailure(
    val throwable: Throwable,
    val response: RestResponse<*>? = null
) : RestActionFailure()
