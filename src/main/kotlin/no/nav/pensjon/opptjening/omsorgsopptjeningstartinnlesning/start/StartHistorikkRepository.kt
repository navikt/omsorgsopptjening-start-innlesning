package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StartHistorikkRepository : JpaRepository<StartHistorikk, Long>