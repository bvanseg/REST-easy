package com.bvanseg.rest.easy.endpoint

/**
 * @author Boston Vanseghi
 */
open class Endpoint(val url: String) {
    fun subPath(path: String): Endpoint = Endpoint(url + path)
}
