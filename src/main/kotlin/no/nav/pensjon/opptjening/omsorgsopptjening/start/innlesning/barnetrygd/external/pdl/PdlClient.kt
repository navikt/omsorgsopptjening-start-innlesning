package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micrometer.core.instrument.MeterRegistry
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
    companion object {
        private val log = LoggerFactory.getLogger(KompletteringsService::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    private val antallPersonerHentet = registry.counter("personer", "antall", "hentet")
    private val restTemplate = RestTemplateBuilder().build()

    @Retryable(
        maxAttempts = 4,
        value = [RestClientException::class, PdlException::class],
        backoff = Backoff(delay = 1500L, maxDelay = 30000L, multiplier = 2.5)
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

        val mapper = ObjectMapper().registerModules(KotlinModule.Builder().build(), JavaTimeModule()).apply {
            propertyNamingStrategy = LOWER_CAMEL_CASE
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }

        val response = responseBody?.let { } // deserialize(responseBody)

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
