package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlService
import org.springframework.stereotype.Service

@Service
class BarnetrygdmottakerKompletteringsService(
    val pdlService: PdlService,
    private val client: BarnetrygdClient,
    private val hjelpestønadService: HjelpestønadService,
) {

    fun kompletter(barnetrygdmottakerUtenPdlData: Barnetrygdmottaker.Mottatt): KomplettertBarnetrygdMottaker {
        val filter = GyldigÅrsintervallFilter(barnetrygdmottakerUtenPdlData.år)

        val personId = pdlService.hentPerson(barnetrygdmottakerUtenPdlData.ident)
        println("%%% PERSON: $personId")

        val barnetrygdmottaker = barnetrygdmottakerUtenPdlData.withPerson(personId)

        val barnetrygdResponse = hentBarnetrygd(personId, filter)

        val barnetrygdRådata = barnetrygdResponse.map { it.rådataFraKilde }

        val persongrunnlag = barnetrygdResponse.map {
            getPersongrunnlag(it)
        }

        val hjelpestønadGrunnlag = persongrunnlag.map {
            hentHjelpestønadGrunnlag(it, filter)
        }
        val hjelpestønadPersongrunnlag = hjelpestønadGrunnlag.flatMap {
            it.map { it.first }
        }
        val hjelpestønadRådata = hjelpestønadGrunnlag.flatMap {
            it.flatMap { it.second }
        }

        val rådata = Rådata(barnetrygdRådata + hjelpestønadRådata)

        val komplettert = KomplettertBarnetrygdMottaker(
            barnetrygdmottaker = barnetrygdmottaker,
            rådata = rådata,
            hjelpestønadPersongrunnlag = hjelpestønadPersongrunnlag,
        )
        return komplettert
    }

    private fun hentHjelpestønadGrunnlag(
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        filter: GyldigÅrsintervallFilter
    ): List<Pair<PersongrunnlagMelding.Persongrunnlag, List<RådataFraKilde>>> {
        return persongrunnlag.map { persongrunnlagMedHjelpestønader(it, filter) }
    }

    private fun hentBarnetrygd(
        personId: PersonId,
        filter: GyldigÅrsintervallFilter
    ): List<HentBarnetrygdResponse> {
        return personId.historiske.map { fnr ->
            client.hentBarnetrygd(
                ident = fnr,
                filter = filter,
            )
        }
    }

    private fun persongrunnlagMedHjelpestønader(
        persongrunnlag: PersongrunnlagMelding.Persongrunnlag,
        filter: GyldigÅrsintervallFilter,
    ): Pair<PersongrunnlagMelding.Persongrunnlag, List<RådataFraKilde>> {
        val hjelpestønad = hjelpestønadService.hentHjelpestønad(
            omsorgsmottakere = persongrunnlag.hentOmsorgsmottakere(),
            filter = filter
        )
        val hjelpestønadRådata = hjelpestønad.map { it.second }
        val hjelpestønadsperioder = hjelpestønad.flatMap { it.first }
        return Pair(persongrunnlag.medHjelpestønadPerioder(hjelpestønadsperioder), hjelpestønadRådata)
    }

    private fun getPersongrunnlag(barnetrygdResponse: HentBarnetrygdResponse) =
        barnetrygdResponse.barnetrygdsaker
            .groupBy { it.omsorgsyter }
            .map { it.value.single() }


    data class KomplettertBarnetrygdMottaker(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val rådata: Rådata,
        val hjelpestønadPersongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
    )
}