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

        val barnetrygdData: PersongrunnlagOgRådata =
            oppdaterAlleFnr(
                hentBarnetrygd(barnetrygdmottaker, gyldigÅrsIntervall)
            ).komprimer()


        val hjelpestønadData =
            oppdaterAlleFnr(
                hentHjelpestønadGrunnlag(barnetrygdData.persongrunnlag, gyldigÅrsIntervall)
            )

        val rådata = Rådata(barnetrygdData.rådataFraKilde + hjelpestønadData.rådataFraKilde)

        return Komplettert(
            barnetrygdmottaker = barnetrygdmottaker,
            persongrunnlag = hjelpestønadData.persongrunnlag,
            rådata = rådata,
        )
    }

    private fun hentHjelpestønadGrunnlag(
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        filter: GyldigÅrsintervallFilter
    ): PersongrunnlagOgRådata {
        return persongrunnlag.map {
            persongrunnlagMedHjelpestønader(it, filter)
        }.let { responses ->
            PersongrunnlagOgRådata(
                persongrunnlag = responses.map { resp -> resp.persongrunnlag },
                rådataFraKilde = responses.flatMap { resp -> resp.rådataFraKilde }
            )
        }
    }

    private fun hentBarnetrygd(
        barnetrygdMottaker: Barnetrygdmottaker.Mottatt,
        gyldigÅrsintervall: GyldigÅrsintervallFilter
    ): PersongrunnlagOgRådata {
        return barnetrygdMottaker.personId!!.historiske.map { fnr ->
            client.hentBarnetrygd(
                ident = fnr,
                gyldigÅrsintervall = gyldigÅrsintervall,
            )
        }.let { responses ->
            PersongrunnlagOgRådata(
                persongrunnlag = responses.flatMap { response -> response.barnetrygdsaker },
                rådataFraKilde = responses.map { response -> response.rådataFraKilde }
            )
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

    data class PersongrunnlagOgRådata(
        val persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        val rådataFraKilde: List<RådataFraKilde>,
    ) {
        fun komprimer(): PersongrunnlagOgRådata {
            val persongrunnlag = persongrunnlag.groupBy { it.omsorgsyter }.map { persongrunnlagPerOmsorgsyter ->
                PersongrunnlagMelding.Persongrunnlag(
                    omsorgsyter = persongrunnlagPerOmsorgsyter.key,
                    omsorgsperioder = persongrunnlagPerOmsorgsyter.value
                        .flatMap { it.omsorgsperioder }
                        .distinct(),
                    hjelpestønadsperioder = persongrunnlagPerOmsorgsyter.value
                        .flatMap { it.hjelpestønadsperioder }
                        .distinct()
                )
            }
            return PersongrunnlagOgRådata(
                persongrunnlag = persongrunnlag,
                rådataFraKilde = this.rådataFraKilde
            )
        }
    }

    fun oppdaterAlleFnr(barnetrygdData: PersongrunnlagOgRådata): PersongrunnlagOgRådata {
        val saker = barnetrygdData.persongrunnlag.map { sak ->
            val omsorgsyter = personIdService.personFromIdent(sak.omsorgsyter)!!.fnr
            val omsorgsperioder = sak.omsorgsperioder.map { omsorgsperiode ->
                val omsorgsmottaker = personIdService.personFromIdent(omsorgsperiode.omsorgsmottaker)!!.fnr
                omsorgsperiode.copy(omsorgsmottaker = omsorgsmottaker)
            }.distinct()
            val hjelpestønadperioder = sak.hjelpestønadsperioder.map {
                it.copy(omsorgsmottaker = personIdService.personFromIdent(it.omsorgsmottaker)!!.fnr)
            }.distinct()
            sak.copy(
                omsorgsyter = omsorgsyter,
                omsorgsperioder = omsorgsperioder,
                hjelpestønadsperioder = hjelpestønadperioder
            )
        }
        return barnetrygdData.copy(persongrunnlag = saker)
    }

    fun ekspanderFnrTilAlleIHistorikken(fnrs: Set<String>): Set<String> {
        return fnrs.flatMap { personIdService.personFromIdent(it)!!.historiske }.toSet()
    }

    data class Komplettert(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        val rådata: Rådata,
    )
}