package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BarnetrygdmottakerRepository: JpaRepository<Barnetrygdmottaker, Long> {
    @Query("select b from barnetrygdmottaker b where b.prosessert=false order by b.opprettet asc limit 1")
    fun finnNesteUprosesserte(): Barnetrygdmottaker?
}