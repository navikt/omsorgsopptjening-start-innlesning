package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClusterConfig {

    @Bean
    fun cluster(
        @Value($$"${NAIS_CLUSTER_NAME}") cluster: String,
    ): NaisCluster {
        return NaisCluster(cluster)
    }
}