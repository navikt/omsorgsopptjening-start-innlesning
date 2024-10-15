package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlService
import org.springframework.stereotype.Service

@Service
class KompletteringsService(
    val pdlService: PdlService,
    private val client: BarnetrygdClient,
    private val hjelpestønadService: HjelpestønadService,
) {

    fun kompletter(barnetrygdmottakerUtenPdlData: Barnetrygdmottaker.Mottatt): Komplettert {
        val filter = GyldigÅrsintervallFilter(barnetrygdmottakerUtenPdlData.år)

        val barnetrygdmottaker = barnetrygdmottakerUtenPdlData.withPerson(
            pdlService.hentPerson(barnetrygdmottakerUtenPdlData.ident)
        )

        val barnetrygdData: BarnetrygdData = hentBarnetrygd(barnetrygdmottaker, filter)

        val persongrunnlag = barnetrygdData.getSanitizedBarnetrygdSaker()

        val hjelpestønadData = hentHjelpestønadGrunnlag(persongrunnlag, filter)

        val rådata = Rådata(barnetrygdData.rådataFraKilde + hjelpestønadData.rådataFraKilde)

        return Komplettert(
            barnetrygdmottaker = barnetrygdmottaker,
            rådata = rådata,
            hjelpestønadPersongrunnlag = hjelpestønadData.persongrunnlag,
        )
    }

    private fun hentHjelpestønadGrunnlag(
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        filter: GyldigÅrsintervallFilter
    ): HjelpestønadData {
        return persongrunnlag.map {
            persongrunnlagMedHjelpestønader(it, filter)
        }.let {
            HjelpestønadData(it)
        }
    }

    private fun hentBarnetrygd(
        barnetrygdMottaker: Barnetrygdmottaker.Mottatt,
        filter: GyldigÅrsintervallFilter
    ): BarnetrygdData {
        return barnetrygdMottaker.personId!!.historiske.map { fnr ->
            client.hentBarnetrygd(
                ident = fnr,
                filter = filter,
            )
        }.let {
            BarnetrygdData(it)
        }
    }

    private fun persongrunnlagMedHjelpestønader(
        persongrunnlag: PersongrunnlagMelding.Persongrunnlag,
        filter: GyldigÅrsintervallFilter,
    ): HjelpestønadResponse {
        val hjelpestønad = hjelpestønadService.hentHjelpestønad(
            omsorgsmottakere = persongrunnlag.hentOmsorgsmottakere(),
            filter = filter
        )
        val hjelpestønadRådata = hjelpestønad.map { it.rådataFraKilde }
        val hjelpestønadsperioder = hjelpestønad.flatMap { it.perioder }
        return HjelpestønadResponse(persongrunnlag.medHjelpestønadPerioder(hjelpestønadsperioder), hjelpestønadRådata)
    }

    data class HjelpestønadResponse(
        val persongrunnlag: PersongrunnlagMelding.Persongrunnlag,
        val rådataFraKilde: List<RådataFraKilde>,
    )

    data class HjelpestønadData(
        private val responses: List<HjelpestønadResponse>
    ) : List<HjelpestønadResponse> by responses {
        val persongrunnlag = responses.map { it.persongrunnlag }
        val rådataFraKilde = responses.flatMap { it.rådataFraKilde }
    }

    data class BarnetrygdData(
        private val response: List<HentBarnetrygdResponse>
    ) : List<HentBarnetrygdResponse> by response {
        val rådataFraKilde = response.map { it.rådataFraKilde }

        fun getSanitizedBarnetrygdSaker(): List<PersongrunnlagMelding.Persongrunnlag> {
            return response
                .flatMap { it.barnetrygdsaker }
                .distinct()
                .groupBy { it.omsorgsyter }
                .map { it.value.single() }
        }
    }

    data class Komplettert(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val rådata: Rådata,
        val hjelpestønadPersongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
    )
}