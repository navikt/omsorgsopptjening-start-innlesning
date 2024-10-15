package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode.Companion.april
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode.Companion.desember
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode.Companion.januar
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode.Companion.mai
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdPeriode
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdSak
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.DelingsprosentYtelse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.HentBarnetrygdDomainMapper
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.Sakstype
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.YtelseTypeEkstern
import org.assertj.core.api.Assertions.assertThat
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test

class HentBarnetrygdDomainMapperTest {
    @Test
    fun `tilpasser periodene i forhold til begrensningene i filteret`() {
        assertThat(
            HentBarnetrygdDomainMapper.map(
                external = listOf(
                    BarnetrygdSak(
                        fagsakEiersIdent = "321",
                        barnetrygdPerioder = listOf(
                            //filtreres vekk
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.DELT,
                                ytelseTypeEkstern = YtelseTypeEkstern.ORDINÆR_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = YearMonth.of(1980, Month.JANUARY),
                                stønadTom = YearMonth.of(1990, Month.JANUARY),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //filtreres vekk
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.DELT,
                                ytelseTypeEkstern = YtelseTypeEkstern.ORDINÆR_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = YearMonth.of(2025, Month.JANUARY),
                                stønadTom = YearMonth.of(999999, Month.JANUARY),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //delvis overlapp, begrenset nedover
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.DELT,
                                ytelseTypeEkstern = YtelseTypeEkstern.ORDINÆR_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = YearMonth.of(2018, Month.JANUARY),
                                stønadTom = YearMonth.of(2020, Month.DECEMBER),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //delvis overlapp, begrenset oppover
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.DELT,
                                ytelseTypeEkstern = YtelseTypeEkstern.ORDINÆR_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = YearMonth.of(2021, Month.JANUARY),
                                stønadTom = YearMonth.of(2024, Month.JANUARY),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //full overlapp
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.DELT,
                                ytelseTypeEkstern = YtelseTypeEkstern.ORDINÆR_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = YearMonth.of(2020, Month.MAY),
                                stønadTom = YearMonth.of(2021, Month.MAY),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            )
                        )
                    )
                ),
                filter = GyldigÅrsintervallFilter(2020)
            )
        ).isEqualTo(
            listOf(
                PersongrunnlagMelding.Persongrunnlag.of(
                    omsorgsyter = "321",
                    omsorgsperioder = listOf(
                        //delvis overlapp, begrenset nedoover
                        PersongrunnlagMelding.Omsorgsperiode(
                            fom = YearMonth.of(2020, Month.JANUARY),
                            tom = YearMonth.of(2020, Month.DECEMBER),
                            omsorgstype = Omsorgstype.DELT_BARNETRYGD,
                            omsorgsmottaker = "123",
                            kilde = Kilde.BARNETRYGD,
                            utbetalt = 7204,
                            landstilknytning = Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                        ),
                        //delvis overlapp, begrenset oppover
                        PersongrunnlagMelding.Omsorgsperiode(
                            fom = YearMonth.of(2021, Month.JANUARY),
                            tom = YearMonth.of(2021, Month.DECEMBER),
                            omsorgstype = Omsorgstype.DELT_BARNETRYGD,
                            omsorgsmottaker = "123",
                            kilde = Kilde.BARNETRYGD,
                            utbetalt = 7204,
                            landstilknytning = Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                        ),
                        //full overlapp
                        PersongrunnlagMelding.Omsorgsperiode(
                            fom = YearMonth.of(2020, Month.MAY),
                            tom = YearMonth.of(2021, Month.MAY),
                            omsorgstype = Omsorgstype.DELT_BARNETRYGD,
                            omsorgsmottaker = "123",
                            kilde = Kilde.BARNETRYGD,
                            utbetalt = 7204,
                            landstilknytning = Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                        ),
                    ),
                    hjelpestønadsperioder = listOf()
                )
            )
        )
    }

    @Test
    fun `filtrerer vekk omsorgsyters perioder med småbarnstillegg og utvidet barnetrygd`() {
        assertThat(
            HentBarnetrygdDomainMapper.map(
                external = listOf(
                    BarnetrygdSak(
                        fagsakEiersIdent = "321",
                        barnetrygdPerioder = listOf(
                            //filtreres vekk
                            BarnetrygdPeriode(
                                personIdent = "321",
                                delingsprosentYtelse = DelingsprosentYtelse.FULL,
                                ytelseTypeEkstern = YtelseTypeEkstern.UTVIDET_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = januar(2020),
                                stønadTom = desember(2020),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //filtreres vekk
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.FULL,
                                ytelseTypeEkstern = YtelseTypeEkstern.SMÅBARNSTILLEGG,
                                utbetaltPerMnd = 7204,
                                stønadFom = januar(2020),
                                stønadTom = desember(2020),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //utvidet fra Infotrygd, skal være med
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.FULL,
                                ytelseTypeEkstern = YtelseTypeEkstern.UTVIDET_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = januar(2020),
                                stønadTom = april(2020),
                                sakstypeEkstern = Sakstype.EØS,
                                kildesystem = BarnetrygdKilde.Infotrygd,
                                pensjonstrygdet = true,
                                norgeErSekundærlandMedNullUtbetaling = null
                            ),
                            //ok
                            BarnetrygdPeriode(
                                personIdent = "123",
                                delingsprosentYtelse = DelingsprosentYtelse.FULL,
                                ytelseTypeEkstern = YtelseTypeEkstern.ORDINÆR_BARNETRYGD,
                                utbetaltPerMnd = 7204,
                                stønadFom = mai(2020),
                                stønadTom = desember(2020),
                                sakstypeEkstern = Sakstype.NASJONAL,
                                kildesystem = BarnetrygdKilde.BA,
                                pensjonstrygdet = null,
                                norgeErSekundærlandMedNullUtbetaling = null
                            )
                        )
                    )
                ),
                filter = GyldigÅrsintervallFilter(2020)
            )
        ).isEqualTo(
            listOf(
                PersongrunnlagMelding.Persongrunnlag.of(
                    omsorgsyter = "321",
                    omsorgsperioder = listOf(
                        PersongrunnlagMelding.Omsorgsperiode(
                            fom = januar(2020),
                            tom = april(2020),
                            omsorgstype = Omsorgstype.FULL_BARNETRYGD,
                            omsorgsmottaker = "123",
                            kilde = Kilde.INFOTRYGD,
                            utbetalt = 7204,
                            landstilknytning = Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                        ),
                        PersongrunnlagMelding.Omsorgsperiode(
                            fom = mai(2020),
                            tom = desember(2020),
                            omsorgstype = Omsorgstype.FULL_BARNETRYGD,
                            omsorgsmottaker = "123",
                            kilde = Kilde.BARNETRYGD,
                            utbetalt = 7204,
                            landstilknytning = Landstilknytning.NORGE
                        ),
                    ),
                    hjelpestønadsperioder = listOf()
                )
            )
        )
    }
}