package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

data class NaisCluster(
    val name: String
) {
    init {
        require(isProd() || isDev()) { "Unknown cluster: $name" }
    }

    fun isProd() = name == "prod-gcp"
    fun isDev() = name == "dev-gcp"
}