package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import java.nio.charset.StandardCharsets

class RestTemplateLoggingInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        println("Request Body: ${String(body,StandardCharsets.UTF_8)}")
        val response = execution.execute(request, body)
        println("Response Body: " + StreamUtils.copyToString(response.body, StandardCharsets.UTF_8))
        return response
    }
}