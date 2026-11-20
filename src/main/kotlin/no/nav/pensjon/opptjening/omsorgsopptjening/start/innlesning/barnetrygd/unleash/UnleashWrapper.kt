package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.unleash

import io.getunleash.Unleash
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

class UnleashWrapper(
    private val unleash: Unleash,
    private val clock: Clock,
) {
    private val cache: MutableMap<NavUnleashConfig.Feature, CacheValue> = mutableMapOf()
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val lock = ReentrantLock()

    fun isEnabled(toggle: NavUnleashConfig.Feature): Boolean {
        return when {
            isCached(toggle) && !isExpired(toggle) -> {
                getCached(toggle).enabled
            }

            isCached(toggle) && isExpired(toggle) -> {
                if (lock.tryLock()) {
                    try {
                        refreshAndGet(toggle)
                    } finally {
                        lock.unlock()
                    }
                } else {
                    getCached(toggle).enabled //use cached value while refresh is in progress
                }
            }

            !isCached(toggle) -> {
                if (lock.tryLock()) {
                    try {
                        refreshAndGet(toggle)
                    } finally {
                        lock.unlock()
                    }
                } else {
                    //refresh might be complete, check for value or return false if still in progress
                    getPotentiallyCached(toggle)?.enabled ?: false
                }
            }

            else -> {
                //refresh might be complete, check for value or return false if still in progress
                getPotentiallyCached(toggle)?.enabled ?: false
            }
        }
    }

    private fun getPotentiallyCached(toggle: NavUnleashConfig.Feature): CacheValue? {
        return cache[toggle]
    }

    private fun getCached(toggle: NavUnleashConfig.Feature): CacheValue {
        require(isCached(toggle))
        return cache[toggle]!!
    }

    private fun isCached(toggle: NavUnleashConfig.Feature): Boolean {
        return cache[toggle] != null
    }

    private fun isExpired(toggle: NavUnleashConfig.Feature): Boolean {
        return isCached(toggle) && getCached(toggle).let { Duration.between(it.timestamp, getNow()).toSeconds() > 30 }
    }

    private fun refreshAndGet(toggle: NavUnleashConfig.Feature): Boolean {
        log.info("Refreshing toggle: ${toggle.toggleName}, existing value: ${getPotentiallyCached(toggle)?.enabled}")
        return unleash.isEnabled(toggle.toggleName)
            .let {
                cache[toggle] = CacheValue(it, getNow())
                log.info("Toggle refreshed: ${toggle.toggleName}, new value: ${getCached(toggle)}")
            }
            .let { getCached(toggle).enabled }
    }

    private fun getNow(): Instant {
        return Instant.now(clock)
    }

    private data class CacheValue(
        val enabled: Boolean,
        val timestamp: Instant
    )
}