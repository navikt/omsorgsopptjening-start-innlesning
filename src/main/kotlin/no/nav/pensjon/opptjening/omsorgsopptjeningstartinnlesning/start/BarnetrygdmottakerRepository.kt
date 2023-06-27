package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BarnetrygdmottakerRepository: JpaRepository<Barnetrygdmottaker, Long> {
}