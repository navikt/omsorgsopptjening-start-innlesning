package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BarnetrygdmottakerRepository: JpaRepository<Barnetrygdmottaker, Long> {
    @Query("""select * from barnetrygdmottaker where status ->> 'type' in ('Klar', 'Retry') limit 1""", nativeQuery = true)
    fun finnNesteUprosesserte(): Barnetrygdmottaker?
}