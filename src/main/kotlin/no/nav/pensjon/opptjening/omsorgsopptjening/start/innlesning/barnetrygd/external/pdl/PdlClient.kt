package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import pensjon.opptjening.azure.ad.client.TokenProvider
import java.net.URI

@Component
class PdlClient(
    @Value("\${PDL_URL}") private val pdlUrl: String,
    @Qualifier("pdlTokenProvider") private val tokenProvider: TokenProvider,
    registry: MeterRegistry,
    private val graphqlQuery: GraphqlQuery,
) {
    private val antallPersonerHentet = registry.counter("personer", "antall", "hentet")
    private val restTemplate = RestTemplateBuilder().build()

    @Retryable(
        maxAttempts = 4,
        value = [RestClientException::class, PdlException::class],
        backoff = Backoff(delay = 1500L, maxDelay = 30000L, multiplier = 2.5)
    )
    fun hentPerson(fnr: Ident): PdlResponse? {
        val entity = RequestEntity<PdlQuery>(
            PdlQuery(graphqlQuery.hentPersonQuery(), FnrVariables(ident = fnr.value)),
            HttpHeaders().apply {
                add("Nav-Call-Id", Mdc.getCorrelationId().toString())
                add("Nav-Consumer-Id", "omsorgsopptjening-start-innlesning")
                add("Tema", "PEN")
                add("behandlingsnummer", "B300")
                add(CorrelationId.identifier, Mdc.getCorrelationId().toString())
                add(InnlesingId.identifier, Mdc.getInnlesingId().toString())
                accept = listOf(MediaType.APPLICATION_JSON)
                contentType = MediaType.APPLICATION_JSON
                setBearerAuth(tokenProvider.getToken())
            },
            HttpMethod.POST,
            URI.create(pdlUrl)
        )
//        restTemplate.interceptors = listOf(RestTemplateLoggingInterceptor())
        val response = restTemplate.exchange(
            entity,
            PdlResponse::class.java
        ).body

        response?.error?.extensions?.code?.also {
            if (it == PdlErrorCode.SERVER_ERROR) throw PdlException(response.error)
        }
        antallPersonerHentet.increment()
        return response
    }
}

@Component
class GraphqlQuery(
    @Value("classpath:pdl/folkeregisteridentifikator.graphql")
    private val hentPersonQuery: Resource,
) {
    fun hentPersonQuery(): String {
        return String(hentPersonQuery.inputStream.readBytes()).replace("[\n\r]", "")
    }
}

private data class PdlQuery(val query: String, val variables: FnrVariables)

private data class FnrVariables(val ident: String)
