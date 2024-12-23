package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.UgyldigPersongrunnlag
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Feilinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.IdentRolle
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class KompletteringsService(
    val personIdService: PersonIdService,
    private val client: BarnetrygdClient,
    private val hjelpestønadService: HjelpestønadService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(KompletteringsService::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    fun kompletter(barnetrygdmottakerUtenPdlData: Barnetrygdmottaker.Mottatt): Komplettert {
        val gyldigÅrsIntervall = GyldigÅrsintervallFilter(barnetrygdmottakerUtenPdlData.år)

        return Komplettering(
            barnetrygdmottaker = barnetrygdmottakerUtenPdlData
        ).andThen { komplettering ->
            try {
                val personIdOgRådata = hentPersonId(
                    fnr = barnetrygdmottakerUtenPdlData.ident,
                    rolle = IdentRolle.BARNETRYGDMOTTAKER,
                    beskrivelse = "barnetrygdmottaker"
                )
                komplettering
                    .withLøseRådata(Rådata(personIdOgRådata.rådata))
                    .withBarnetrygdMottaker(
                        barnetrygdmottakerUtenPdlData.withPerson(
                            personIdOgRådata.value,
                        )
                    )
            } catch (e: BarnetrygdException.FeilVedHentingAvPersonId) {
                secureLog.warn("Feil ved oppdatering av ident for barnetrygdmottaker", e)
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.UgyldigIdent(
                            message = "Feil ved oppdatering av ident for barnetrygdmottaker",
                            exceptionMessage = e.cause?.message ?: "",
                            exceptionType = e.cause?.javaClass?.canonicalName ?: "",
                            ident = barnetrygdmottakerUtenPdlData.ident.value,
                            identRolle = IdentRolle.BARNETRYGDMOTTAKER,
                        )
                    )
            }
        }.andThen { komplettering ->
            try {
                komplettering.withPersongrunnlag(
                    hentBarnetrygd(komplettering.barnetrygdmottaker, gyldigÅrsIntervall)
                )
            } catch (e: BarnetrygdException.OverlappendePerioder) {
                secureLog.warn("Feil ved henting av barnetrygd. Overlappende perioder: ${e.perioder}", e)
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.OverlappendeBarnetrygdperioder(
                            message = "Feil i datagrunnlag: ${e.message}",
                            exceptionType = e::class.java.canonicalName,
                            exceptionMessage = e.message ?: "",
                            omsorgsmottaker = e.omsorgsmottaker,
                        )
                    )
            } catch (e: BarnetrygdException.FeilIGrunnlagsdata) {
                secureLog.warn("Feil ved henting av barnetrygd", e)
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.FeilIDataGrunnlag(
                            message = "Feil i datagrunnlag: ${e.message}",
                            exceptionType = e::class.java.canonicalName,
                            exceptionMessage = e.message ?: "",
                        )
                    )
            }
        }.andThen { komplettering ->
            try {
                val persongrunnlagOgRådata = oppdaterAlleFnr(komplettering.persongrunnlag!!)
                komplettering.withPersongrunnlag(
                    persongrunnlagOgRådata
                )
            } catch (e: BarnetrygdException.FeilVedHentingAvPersonId) {
                secureLog.warn("Feil ved oppdatering av fødselsnummer etter henting av barnetrygdgrunnlag", e)
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.UgyldigIdent(
                            message = "Feil ved oppdatering av ident for omsorgsmottaker",
                            exceptionType = e.cause?.javaClass?.canonicalName ?: "",
                            exceptionMessage = e.cause?.message ?: "",
                            ident = e.fnr.value,
                            identRolle = e.rolle,
                        )
                    )
            } catch (e: BarnetrygdException.OverlappendePerioder) {
                secureLog.warn(
                    "Feil ved oppdatering av fødselsnummer etter henting av barnetrygdgrunnlag. Overlappende perioder: ${e.perioder}",
                    e
                )
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.OverlappendeBarnetrygdperioder(
                            message = e.message ?: "",
                            exceptionType = e.cause?.let { it::class.java.canonicalName } ?: "",
                            exceptionMessage = e.cause?.message ?: "",
                            omsorgsmottaker = e.omsorgsmottaker,
                        )
                    )
            }
        }.andThen { komplettering ->
            try {
                komplettering.withPersongrunnlag(
                    komplettering.persongrunnlag!!.komprimer()
                )
            } catch (e: BarnetrygdException.OverlappendePerioder) {
                secureLog.warn(
                    "Feil ved komprimering av persongrunnlag etter henting av barnetrygdgrunnlag. Overlappende perioder: ${e.perioder}",
                    e
                )
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.OverlappendeBarnetrygdperioder(
                            message = e.message ?: "",
                            exceptionType = e.cause?.let { it::class.java.canonicalName } ?: "",
                            exceptionMessage = e.cause?.message ?: "",
                            omsorgsmottaker = e.omsorgsmottaker,
                        )
                    )
            }
        }.andThen { komplettering ->
            try {
                komplettering.withPersongrunnlag(
                    hentHjelpestønadGrunnlag(komplettering.persongrunnlag!!, gyldigÅrsIntervall)
                )
            } catch (e: UgyldigPersongrunnlag.OverlappendeOmsorgsperiode) {
                secureLog.warn("Overlappende perioder ved henting av hjelpestønadsgrunnlag: ${e.perioder}", e)
                komplettering
                    .withFeilinformasjon(
                        Feilinformasjon.OverlappendeHjelpestønadperioder(
                            message = "Overlappende perioder ved henting av hjelpestønadsgrunnlag",
                            exceptionType = e::class.java.canonicalName,
                            exceptionMessage = e.message ?: "",
                            omsorgsmottaker = e.omsorgsmottaker,
                        )
                    )
            } catch (e: UgyldigPersongrunnlag) {
                secureLog.warn("Feil ved henting av hjelpestønadgrunnlag", e)
                komplettering.withFeilinformasjon(
                    Feilinformasjon.FeilIDataGrunnlag(
                        message = "Feil i datagrunnlag ved henting av hjelpestønadgrunnlag",
                        exceptionType = e::class.java.canonicalName,
                        exceptionMessage = e.message ?: "",
                    )
                )
            }
        }.andThen { komplettering ->
            try {
                komplettering.withPersongrunnlag(
                    oppdaterAlleFnr(
                        komplettering.persongrunnlag!!
                    )
                )
            } catch (e: BarnetrygdException.FeilVedHentingAvPersonId) {
                secureLog.warn("Feil ved oppdatering av fødselsnummer etter henting av hjelpestønadgrunnlag", e)
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.UgyldigIdent(
                            message = "Feil ved oppdatering av ident for hjelpestønadmottager",
                            exceptionMessage = e.cause?.message ?: "",
                            exceptionType = e.cause?.javaClass?.canonicalName ?: "",
                            ident = e.fnr.value,
                            identRolle = e.rolle,
                        )
                    )
            } catch (e: BarnetrygdException.OverlappendePerioder) {
                secureLog.warn(
                    "Feil ved oppdatering av fødselsnummer etter henting av hjelpestønadgrunnlag. Overlappende perioder: ${e.perioder}",
                    e
                )
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.OverlappendeHjelpestønadperioder(
                            message = "Overlappende hjelpestønadperioder",
                            exceptionType = e::class.java.canonicalName,
                            exceptionMessage = e.message ?: "",
                            omsorgsmottaker = e.omsorgsmottaker,
                        )
                    )
            }
        }.andThen { komplettering ->
            try {
                komplettering.withPersongrunnlag(
                    komplettering.persongrunnlag!!.komprimer()
                )
            } catch (e: BarnetrygdException.OverlappendePerioder) {
                secureLog.warn(
                    "Overlappende perioder ved komprimering av persongrunnlag etter henting av hjelpestønadgrunnlag: ${e.perioder}",
                    e
                )
                komplettering
                    .withLøseRådata(e.rådata)
                    .withFeilinformasjon(
                        Feilinformasjon.OverlappendeHjelpestønadperioder(
                            message = e.message ?: "",
                            exceptionType = e.cause?.let { it::class.java.canonicalName } ?: "",
                            exceptionMessage = e.cause?.message ?: "",
                            omsorgsmottaker = e.omsorgsmottaker,
                        )
                    )
            }
        }.mapTo(
            whenOk = { komplettering ->
                Komplettert(
                    barnetrygdmottaker = komplettering.barnetrygdmottaker,
                    persongrunnlag = komplettering.persongrunnlag!!.persongrunnlag,
                    rådata = Rådata(komplettering.akkumulertRådata().distinct()),
                )
            },
            whenFeilet = { komplettering ->
                Komplettert(
                    barnetrygdmottaker = komplettering.barnetrygdmottaker,
                    persongrunnlag = emptyList(),
                    feilinformasjon = komplettering.feilinformasjon?.let { listOf(it) } ?: emptyList(),
                    rådata = Rådata(komplettering.akkumulertRådata().distinct()),
                )
            }
        )
    }

    private fun hentHjelpestønadGrunnlag(
        persongrunnlagOgRådata: PersongrunnlagOgRådata,
        filter: GyldigÅrsintervallFilter
    ): PersongrunnlagOgRådata {
        return persongrunnlagOgRådata.persongrunnlag.map {
            persongrunnlagMedHjelpestønader(it, filter)
        }.let { responses ->
            PersongrunnlagOgRådata(
                persongrunnlag = responses.map { resp -> resp.persongrunnlag },
                rådataFraKilde = persongrunnlagOgRådata.rådataFraKilde + responses.flatMap { resp -> resp.rådataFraKilde }
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
            omsorgsmottakere = omsorgsmottakere.map { Ident(it) }.toSet(),
            filter = gyldigÅrsintervall
        )
        val hjelpestønadRådata = hjelpestønad.map { it.rådataFraKilde }
        val hjelpestønadsperioder = hjelpestønad.flatMap { it.perioder }
        return HjelpestønadResponse(
            persongrunnlag.medHjelpestønadPerioder(hjelpestønadsperioder),
            hjelpestønadRådata
        )
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
                    rådataFraKilde = this.rådataFraKilde,
                )
            } catch (e: UgyldigPersongrunnlag.OverlappendeOmsorgsperiode) {
                throw BarnetrygdException.OverlappendePerioder(
                    msg = "Overlappende perioder for samme omsorgsmottaker",
                    cause = e,
                    perioder = e.perioder,
                    omsorgsmottaker = e.omsorgsmottaker,
                    rådata = rådataFraKilde,
                )
            } catch (e: UgyldigPersongrunnlag) {
                throw BarnetrygdException.FeilIGrunnlagsdata(
                    msg = "Feil i datagrunnlag ved komprimering av persongrunnlag",
                    cause = e,
                    rådata = rådataFraKilde,
                )
            }
        }
    }

    fun oppdaterAlleFnr(barnetrygdData: PersongrunnlagOgRådata): PersongrunnlagOgRådata {
        val rådata: MutableList<RådataFraKilde> = mutableListOf() // TODO: finne en måte å komme unna mutable
        try {
            val saker = barnetrygdData.persongrunnlag.map { sak ->
                val personIdOgRådataForOmsorgsyter = hentPersonId(
                    fnr = Ident(value = sak.omsorgsyter),
                    rolle = IdentRolle.OMSORGSYTER_BARNETRYGD,
                    beskrivelse = "omsorgsyter",
                )
                val omsorgsyter = personIdOgRådataForOmsorgsyter.value.fnr
                rådata.addAll(personIdOgRådataForOmsorgsyter.rådata)
                val omsorgsperioderMedrådata = sak.omsorgsperioder.map { oppdaterAlleFnr(it) }.distinct()
                rådata.addAll(omsorgsperioderMedrådata.flatMap { it.rådata })
                val hjelpestønadperioderMedRådata = sak.hjelpestønadsperioder.map { oppdaterAlleFnr(it) }.distinct()
                rådata.addAll(hjelpestønadperioderMedRådata.flatMap { it.rådata })

                MedRådata(
                    PersongrunnlagMelding.Persongrunnlag.of(
                        omsorgsyter = omsorgsyter.value,
                        omsorgsperioder = omsorgsperioderMedrådata.map { it.value },
                        hjelpestønadsperioder = hjelpestønadperioderMedRådata.map { it.value },
                    ),
                    rådata = rådata
                )
            }
            return barnetrygdData.copy(
                persongrunnlag = saker.map { it.value },
                rådataFraKilde = barnetrygdData.rådataFraKilde + saker.flatMap { it.rådata }
            )
        } catch (e: UgyldigPersongrunnlag.OverlappendeOmsorgsperiode) {
            throw BarnetrygdException.OverlappendePerioder(
                msg = "Overlappende perioder",
                cause = e,
                perioder = e.perioder,
                omsorgsmottaker = e.omsorgsmottaker,
                rådata = rådata,
            )
        }
    }

    private fun oppdaterAlleFnr(
        hjelpestønadperiode: PersongrunnlagMelding.Hjelpestønadperiode,
    ): MedRådata<PersongrunnlagMelding.Hjelpestønadperiode> {
        val personIdOgRådata = hentPersonId(
            fnr = Ident(value = hjelpestønadperiode.omsorgsmottaker),
            rolle = IdentRolle.OMSORGSMOTTAKER_HJELPESTONAD,
            beskrivelse = "omsorgsmottaker, hjelpestønad"
        )
        return MedRådata(
            value = hjelpestønadperiode.copy(
                omsorgsmottaker = personIdOgRådata.value.fnr.value
            ),
            rådata = personIdOgRådata.rådata
        )
    }

    private fun oppdaterAlleFnr(
        omsorgsperiode: PersongrunnlagMelding.Omsorgsperiode
    ): MedRådata<PersongrunnlagMelding.Omsorgsperiode> {
        val personIdOgRådata = hentPersonId(
            fnr = Ident(omsorgsperiode.omsorgsmottaker),
            rolle = IdentRolle.OMSORGSMOTTAKER_BARNETRYGD,
            beskrivelse = "omsorgsmottaker, barnetrygd"
        )
        val omsorgsmottaker = personIdOgRådata.value.fnr
        return MedRådata(
            value = omsorgsperiode.copy(omsorgsmottaker = omsorgsmottaker.value),
            rådata = personIdOgRådata.rådata
        )
    }

    private fun hentPersonId(fnr: Ident, rolle: IdentRolle, beskrivelse: String): MedRådata<PersonId> {
        try {
            return personIdService.personFromIdent(fnr)!!
        } catch (e: PersonOppslagException) {
            throw BarnetrygdException.FeilVedHentingAvPersonId(
                fnr = fnr,
                rolle = rolle,
                msg = "Feil ved oppslag i PDL for '$beskrivelse'",
                cause = e,
                rådata = e.rådata,
            )
        }
    }

    fun ekspanderFnrTilAlleIHistorikken(fnrs: Set<String>): Set<String> {
        return fnrs.flatMap { personIdService.personFromIdent(Ident(it))!!.value.historiske }.toSet()
    }

    data class Komplettert(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        val feilinformasjon: List<Feilinformasjon> = emptyList(),
        val rådata: Rådata,
    )


    data class Komplettering(
        val barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        val feilinformasjon: Feilinformasjon? = null,
        val persongrunnlag: PersongrunnlagOgRådata? = null,
        val løseRådata: List<RådataFraKilde> = emptyList(),
    ) {
        val feilet = feilinformasjon != null

        fun andThen(block: (Komplettering) -> Komplettering): Komplettering {
            return if (feilet) {
                this
            } else {
                block(this)
            }
        }

        fun akkumulertRådata(): Rådata {
            val rådataFraPersongrunnlag = persongrunnlag?.rådataFraKilde ?: emptyList()
            val rådataFraKilder = rådataFraPersongrunnlag + løseRådata
            return Rådata(rådataFraKilder)
        }

        fun <T> mapTo(
            whenOk: (Komplettering) -> T,
            whenFeilet: (Komplettering) -> T
        ): T {
            return if (feilet) {
                whenFeilet(this)
            } else {
                return whenOk(this)
            }
        }

        fun withBarnetrygdMottaker(barnetrygdmottaker: Barnetrygdmottaker.Mottatt): Komplettering {
            return copy(barnetrygdmottaker = barnetrygdmottaker)
        }

        fun withPersongrunnlag(barnetrygdData: PersongrunnlagOgRådata): Komplettering {
            return copy(persongrunnlag = barnetrygdData)
        }

        fun withFeilinformasjon(feilinformasjon: Feilinformasjon): Komplettering {
            return copy(feilinformasjon = feilinformasjon)
        }

        fun withLøseRådata(rådata: List<RådataFraKilde>?): Komplettering {
            return if (rådata == null) {
                this
            } else {
                return copy(løseRådata = løseRådata + rådata)
            }
        }
    }
}