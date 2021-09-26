package com.bvanseg.rest.easy.client

import com.bvanseg.rest.easy.BodyTransformer
import java.net.http.HttpClient

/**
 * @author Boston Vanseghi
 */
class RestClient(val bodyTransformer: BodyTransformer, val httpClient: HttpClient)