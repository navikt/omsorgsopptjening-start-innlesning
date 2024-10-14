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

        val barnetrygdResponse = hentBarnetrygd(barnetrygdmottaker, filter)

        val persongrunnlag = barnetrygdResponse.map {
            getPersongrunnlag(it)
        }

        val hjelpestønadGrunnlag = persongrunnlag.map {
            hentHjelpestønadGrunnlag(it, filter)
        }
        val hjelpestønadPersongrunnlag = hjelpestønadGrunnlag.flatMap {
            it.map { pair -> pair.first }
        }

        val barnetrygdRådata = barnetrygdResponse.map { it.rådataFraKilde }

        val hjelpestønadRådata = hjelpestønadGrunnlag.flatMap {
            it.flatMap { pair -> pair.second }
        }

        val rådata = Rådata(barnetrygdRådata + hjelpestønadRådata)

        return Komplettert(
            barnetrygdmottaker = barnetrygdmottaker,
            rådata = rådata,
            hjelpestønadPersongrunnlag = hjelpestønadPersongrunnlag,
        )
    }

    private fun hentHjelpestønadGrunnlag(
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        filter: GyldigÅrsintervallFilter
    ): List<Pair<PersongrunnlagMelding.Persongrunnlag, List<RådataFraKilde>>> {
        return persongrunnlag.map { persongrunnlagMedHjelpestønader(it, filter) }
    }

    private fun hentBarnetrygd(
        barnetrygdMottaker: Barnetrygdmottaker.Mottatt,
        filter: GyldigÅrsintervallFilter
    ): List<HentBarnetrygdResponse> {
        return barnetrygdMottaker.personId!!.historiske.map { fnr ->
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
        val hjelpestønadRådata = hjelpestønad.map { it.rådataFraKilde }
        val hjelpestønadsperioder = hjelpestønad.flatMap { it.perioder }
        return Pair(persongrunnlag.medHjelpestønadPerioder(hjelpestønadsperioder), hjelpestønadRådata)
    }

    private fun getPersongrunnlag(barnetrygdResponse: HentBarnetrygdResponse) =
        barnetrygdResponse.barnetrygdsaker
            .groupBy { it.omsorgsyter }
            .map { it.value.single() }


    data class Komplettert(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val rådata: Rådata,
        val hjelpestønadPersongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
    )
}