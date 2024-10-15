package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdClient
import org.springframework.stereotype.Service

@Service
class KompletteringsService(
    val personIdService: PersonIdService,
    private val client: BarnetrygdClient,
    private val hjelpestønadService: HjelpestønadService,
) {

    fun kompletter(barnetrygdmottakerUtenPdlData: Barnetrygdmottaker.Mottatt): Komplettert {
        val gyldigÅrsIntervall = GyldigÅrsintervallFilter(barnetrygdmottakerUtenPdlData.år)

        val barnetrygdmottaker = barnetrygdmottakerUtenPdlData.withPerson(
            // TODO: håndter manglende svar
            personIdService.personFromIdent(barnetrygdmottakerUtenPdlData.ident)!!
        )

        val barnetrygdData: BarnetrygdData =
            oppdaterAlleFnr(
                hentBarnetrygd(barnetrygdmottaker, gyldigÅrsIntervall)
            )

        val persongrunnlag = barnetrygdData.getSanitizedBarnetrygdSaker()

        val hjelpestønadData = hentHjelpestønadGrunnlag(persongrunnlag, gyldigÅrsIntervall)

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
        gyldigÅrsintervall: GyldigÅrsintervallFilter
    ): BarnetrygdData {
        return barnetrygdMottaker.personId!!.historiske.map { fnr ->
            client.hentBarnetrygd(
                ident = fnr,
                gyldigÅrsintervall = gyldigÅrsintervall,
            )
        }.let {
            BarnetrygdData(it)
        }
    }

    private fun persongrunnlagMedHjelpestønader(
        persongrunnlag: PersongrunnlagMelding.Persongrunnlag,
        gyldigÅrsintervall: GyldigÅrsintervallFilter,
    ): HjelpestønadResponse {
        val omsorgsmottakere = ekspanderFnrTilAlleIHistorikken(persongrunnlag.hentOmsorgsmottakere())
        val hjelpestønad = hjelpestønadService.hentHjelpestønad(
            omsorgsmottakere = omsorgsmottakere,
            filter = gyldigÅrsintervall
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
        val responses: List<HentBarnetrygdResponse>
    ) : List<HentBarnetrygdResponse> by responses {
        val rådataFraKilde = responses.map { it.rådataFraKilde }

        fun getSanitizedBarnetrygdSaker(): List<PersongrunnlagMelding.Persongrunnlag> {
            return responses
                .flatMap { it.barnetrygdsaker }
                .distinct()
                .groupBy { it.omsorgsyter }
                .map { it.value.single() }
        }
    }

    fun oppdaterAlleFnr(barnetrygdData: BarnetrygdData): BarnetrygdData {
        val responses = barnetrygdData.responses.map { resp ->
            val saker = resp.barnetrygdsaker.map { sak ->
                val omsorgsyter = personIdService.personFromIdent(sak.omsorgsyter)!!.fnr
                val omsorgsperioder = sak.omsorgsperioder.map { omsorgsperiode ->
                    val omsorgsmottaker = personIdService.personFromIdent(omsorgsperiode.omsorgsmottaker)!!.fnr
                    omsorgsperiode.copy(omsorgsmottaker = omsorgsmottaker)
                }
                sak.copy(omsorgsyter = omsorgsyter, omsorgsperioder = omsorgsperioder)
            }
            resp.copy(barnetrygdsaker = saker)
        }
        return barnetrygdData.copy(responses = responses)
    }

    fun ekspanderFnrTilAlleIHistorikken(fnrs: Set<String>): Set<String> {
        return fnrs.flatMap { personIdService.personFromIdent(it)!!.historiske }.toSet()
    }

    data class Komplettert(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val rådata: Rådata,
        val hjelpestønadPersongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
    )
}