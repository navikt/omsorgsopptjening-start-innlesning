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
            try {
                hentPersonId(barnetrygdmottakerUtenPdlData.ident, "barnetrygdmottaker")
            } catch (e: PersonOppslagException) {
                throw BarnetrygdException.FeilVedHentingAvPersonId(
                    fnr = barnetrygdmottakerUtenPdlData.ident,
                    msg = "Feil ved henting av barnetrygdmottaker fra PDL",
                    cause = e,
                )
            }
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

        val komplettert = Komplettert(
            barnetrygdmottaker = barnetrygdmottaker,
            persongrunnlag = hjelpestønadData.persongrunnlag,
            rådata = rådata,
        )
        return komplettert
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
            try {
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
            } catch (e: IllegalArgumentException) {
                throw BarnetrygdException.OverlappendePerioder("Overlappende perioder for samme omsorgsmottaker", e)
            }
        }
    }

    fun oppdaterAlleFnr(barnetrygdData: PersongrunnlagOgRådata): PersongrunnlagOgRådata {
        try {
            val saker = barnetrygdData.persongrunnlag.map { sak ->
                val omsorgsyter = hentPersonId(sak.omsorgsyter, "omsorgsyter").fnr
                val omsorgsperioder = sak.omsorgsperioder.map { omsorgsperiode ->
                    val omsorgsmottaker =
                        hentPersonId(omsorgsperiode.omsorgsmottaker, "omsorgsmottaker, barnetrygd").fnr
                    omsorgsperiode.copy(omsorgsmottaker = omsorgsmottaker)
                }.distinct()
                val hjelpestønadperioder = sak.hjelpestønadsperioder.map {
                    it.copy(omsorgsmottaker = hentPersonId(it.omsorgsmottaker, "omsorgsmottaker, hjelpestønad").fnr)
                }.distinct()
                sak.copy(
                    omsorgsyter = omsorgsyter,
                    omsorgsperioder = omsorgsperioder,
                    hjelpestønadsperioder = hjelpestønadperioder
                )
            }
            return barnetrygdData.copy(persongrunnlag = saker)
        } catch (e: IllegalArgumentException) {
            if ("Overlappende perioder for samme omsorgsmottaker" == e.message) {
                throw BarnetrygdException.OverlappendePerioder("Overlappende hjelpestønadsperioder", e)
            } else {
                throw e
            }
        }
    }

    private fun hentPersonId(fnr: String, beskrivelse: String): PersonId {
        try {
            return personIdService.personFromIdent(fnr)!!
        } catch (e: PersonOppslagException) {
            throw BarnetrygdException.FeilVedHentingAvPersonId(fnr, "Feil ved oppslag i PDL for '$beskrivelse'", e)
        }
    }

    fun ekspanderFnrTilAlleIHistorikken(fnrs: Set<String>): Set<String> {
        return fnrs.flatMap { personIdService.personFromIdent(it)!!.historiske }.toSet()
    }

    data class Komplettert(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        val rådata: Rådata,
    ) {
        init {
            // valider()
        }

        fun valider() {
            /*
                TODO: Mulige regler:
                - minimum én omsorgsperiode per barnetrygdmottaker
                - maksimum én periode per barnetrygdmottaker (var der tidligere, men usikker på om det fortsatt
                  er gjeldende når man sjekker historike fnr)
                - sjekker på hjelpestønad?
                (sjekk på overlappende perioder ligger i omsorgsopptjening-domene-lib)

                Original kommentar:
                Tillater dette for å få kjørt ferdig en del rest-saker fra prodkjøringen.
                Gjenstående tilfeller er en blanding av saker som ikke burde vært sendt til oss
                i første omgang, og saker hvor relaterte saker ikke har noen perioder. Siden
                det er fair å anta at noen av de sistnevnte er aktuell for godskriving, prøver vi
                å behandle selv om det potensielt ikke eksisterer noen perioder på verken bruker
                eller relatert.
            */
        }
    }
}