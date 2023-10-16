package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource


@Configuration
class TestDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.test")
    fun testDataSourceProperties() : DataSourceProperties {
        return DataSourceProperties()
    }

//    @Bean
    @ConfigurationProperties("spring.datasource.testmonitoring")
    fun testMonitoringDataSourceProperties() : DataSourceProperties {
        return DataSourceProperties()
    }

    @Bean
    fun testDataSource() : DataSource {
        return testDataSourceProperties().initializeDataSourceBuilder().build()
    }

    @Bean
    fun testJdbcTemplate(@Qualifier("testDataSource") dataSource: DataSource): NamedParameterJdbcTemplate {
        return NamedParameterJdbcTemplate(dataSource)
    }

    /*
    @Bean
    fun topicsJdbcTemplate(@Qualifier("testmonitoringDataSource") dataSource: DataSource?): NamedParameterJdbcTemplate {
        return NamedParameterJdbcTemplate(dataSource)
    }
     */
}