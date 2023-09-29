package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration

@Configuration
@EnableJwtTokenValidation
class TokenValidationConfig