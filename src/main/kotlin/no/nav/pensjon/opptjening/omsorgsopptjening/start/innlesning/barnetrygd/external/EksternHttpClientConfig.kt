package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import java.nio.charset.StandardCharsets
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate

/**
 * Delt tilkoblingspool for de eksterne HTTP-klientene (barnetrygd, hjelpestønad).
 *
 * To regresjoner fra Spring Boot 4 (WebClient -> httpclient5), begge uavhengige av RestClient/RestTemplate:
 *  1) Charset: se utf8RestTemplate under (tegnsalat på æ/ø/å).
 *  2) Stale keep-alive: httpclient5 gjenbrukte tilkoblinger serveren allerede hadde lukket. validateAfterInactivity
 *     revaliderer før gjenbruk, men er racy (LB kan lukke inni vinduet), så retry-strategien tar resten.
 *     POST-ene er lesespørringer, så retry er trygt.
 *
 * Ingen micrometer pool-metrics her; legg til om vi trenger pool-observabilitet i Grafana.
 */
@Configuration
class EksternHttpClientConfig {

    @Bean
    fun eksternClientHttpRequestFactory(): ClientHttpRequestFactory {
        val connectionManager = PoolingHttpClientConnectionManager().apply {
            defaultMaxPerRoute = 12
            maxTotal = 64
            setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(30))
                    .setSocketTimeout(Timeout.ofSeconds(30))
                    .setTimeToLive(TimeValue.ofSeconds(60))
                    .setValidateAfterInactivity(Timeout.ofSeconds(2))
                    .build()
            )
        }
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .evictExpiredConnections()
            // Prøv på nytt ved tilkoblingsbrudd (f.eks. NoHttpResponseException fra en stale keep-alive-tilkobling). handleAsIdempotent=true fordi våre POST-er er lesespørringer, ikke mutasjoner.
            .setRetryStrategy(object : DefaultHttpRequestRetryStrategy(2, TimeValue.ofMilliseconds(200)) {
                override fun handleAsIdempotent(request: HttpRequest): Boolean = true
            })
            .build()
        return HttpComponentsClientHttpRequestFactory(httpClient)
    }
}

/**
 * RestTemplate som dekoder String-svar som UTF-8 uansett (StringHttpMessageConverter defaulter til
 * ISO-8859-1 når svaret mangler charset i Content-Type – det ga tegnsalat på æ/ø/å, f.eks. når
 * barnetrygd svarer 500 uten Content-Type). NAV-tjenestene er UTF-8. WebClient (før migreringa)
 * defaultet til UTF-8, dette gjenoppretter den oppførselen.
 */
fun utf8RestTemplate(requestFactory: ClientHttpRequestFactory): RestTemplate {
    return RestTemplate(requestFactory).apply {
        messageConverters.replaceAll {
            if (it is StringHttpMessageConverter) StringHttpMessageConverter(StandardCharsets.UTF_8) else it
        }
    }
}
