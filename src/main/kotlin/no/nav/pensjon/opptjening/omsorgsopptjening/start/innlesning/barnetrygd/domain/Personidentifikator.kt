package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import kotlin.random.Random

@JvmInline
value class Personidentifikator private constructor(val value: String) {

    companion object {
        private val FORMAT = Regex("[0-9]{11}")

        fun fra(value: String): Personidentifikator? {
            return value.takeIf(FORMAT::matches)?.let(::Personidentifikator)
        }

        fun krev(value: String): Personidentifikator {
            return fra(value) ?: throw IllegalArgumentException("Ugyldig personidentifikator")
        }

        fun random(): Personidentifikator = Personidentifikator(
            Random.nextLong(10000000000L, 99999999999L).toString()
        )
    }
}
