package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import io.micrometer.core.instrument.MeterRegistry
import java.net.URI
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.KompletteringsService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.MedRådata
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import pensjon.opptjening.azure.ad.client.TokenProvider
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Component
class PdlClient(
    @Value("\${PDL_URL}") private val pdlUrl: String,
    @Qualifier("pdlTokenProvider") private val tokenProvider: TokenProvider,
    registry: MeterRegistry,
    private val graphqlQuery: GraphqlQuery,
    requestFactory: ClientHttpRequestFactory,
) {
    companion object {
        private val log = LoggerFactory.getLogger(KompletteringsService::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    private val antallPersonerHentet = registry.counter("personer", "antall", "hentet")
    private val restTemplate = RestTemplate(requestFactory)

    @Retryable(
        maxAttempts = 4,
        value = [RestClientException::class, PdlException::class],
        backoff = Backoff(delayExpression = "\${pdl.retry.delayMs:1500}", maxDelay = 30000L, multiplier = 2.5)
    )
    fun hentPerson(fnr: Ident): MedRådata<PdlResponse>? {
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

        val responseBody = restTemplate.exchange(
            entity,
            String::class.java
        ).body

        antallPersonerHentet.increment()
        return responseBody?.let { body ->
            val response = mapper.readValue(body, PdlResponse::class.java)
            response?.error?.extensions?.code?.also { code ->
                if (code == PdlErrorCode.SERVER_ERROR) throw PdlException(
                    pdlError = response.error,
                    rådata = listOf(RådataFraKilde(mapOf(fnr.value to body)))
                )
            }
            MedRådata(
                response,
                Rådata(
                    listOf(
                        RådataFraKilde(
                            mapOf(
                                fnr.value to body
                            )
                        )
                    )
                )
            )
        }
    }

    // Gjenopprettet eksplisitt mapper-konfig fra før migreringen, låser kontrakten mot PDL
    // uavhengig av Jackson-defaults.
    // FAIL_ON_UNKNOWN_PROPERTIES=false: PDL kan legge til nye felt i svaret uten at deserialiseringen
    // feiler. Flere av PDL-DTO-ene (PdlResponse, PdlError, Extensions) mangler @JsonIgnoreProperties,
    // så uten dette flagget vil nye felt fra PDL krasje appen.
    // Hvis PDL en gang sender ukjente enum-verdier: gjør status/type nullbare + slå på
    // READ_UNKNOWN_ENUM_VALUES_AS_NULL.
    private val mapper = jacksonMapperBuilder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()
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
