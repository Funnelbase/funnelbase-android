package com.funnelbase.demo.trading.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Invariants of the generated data.
 *
 * The prices are random by design, so these assert the *shape* rather than any value — which is
 * exactly what caught the bug these tests were written after: an `abs()` in the rounding helper
 * meant to guard prices also stripped the sign off every change, and the entire market rendered
 * green. A hundred launches would not have looked wrong enough to notice.
 */
class MarketDataTest {

    private fun generate(seed: Int) = MarketData.generate(Random(seed))

    @Test
    fun `fifty instruments, all distinct`() {
        val instruments = generate(1).instruments
        assertEquals(50, instruments.size)
        assertEquals(50, instruments.map(Instrument::symbol).toSet().size)
    }

    @Test
    fun `changes go both ways`() {
        // Across 50 instruments a fair coin gives all-one-direction odds of about 1 in 5.6e14.
        // If this fails, the sign is being lost somewhere, not drawn unluckily.
        val changes = generate(7).instruments.map(Instrument::changePercent)
        assertTrue(changes.any { it < 0 }, "no instrument fell — is the sign being stripped?")
        assertTrue(changes.any { it > 0 }, "no instrument rose")
    }

    @Test
    fun `the day's range contains the day`() {
        generate(3).instruments.forEach {
            assertTrue(it.dayLow <= it.price && it.price <= it.dayHigh) { "${it.symbol}: price outside range" }
            assertTrue(it.dayLow <= it.open && it.open <= it.dayHigh) { "${it.symbol}: open outside range" }
            assertTrue(it.dayLow <= it.previousClose && it.previousClose <= it.dayHigh) {
                "${it.symbol}: previous close outside range"
            }
        }
    }

    @Test
    fun `change percent agrees with previous close`() {
        generate(5).instruments.forEach {
            val implied = (it.price - it.previousClose) / it.previousClose * 100
            assertTrue(abs(implied - it.changePercent) < 0.01) {
                "${it.symbol}: ${it.changePercent}% stated, ${implied}% implied"
            }
        }
    }

    @Test
    fun `order cost is quantity times price`() {
        generate(9).orders.forEach {
            assertEquals(it.quantity * it.price, it.cost, 1e-9)
        }
    }

    @Test
    fun `every order status is represented, so no chip is ever empty`() {
        val statuses = generate(11).orders.map(Order::status).toSet()
        assertEquals(OrderStatus.entries.toSet(), statuses)
    }

    @Test
    fun `portfolio total is invested plus cash, and day change is summed from holdings`() {
        val portfolio = generate(13).portfolio
        assertEquals(portfolio.investedValue + portfolio.cashUsd, portfolio.totalValue, 1e-9)
        assertEquals(portfolio.holdings.sumOf { it.quantity * it.instrument.changeAbsolute }, portfolio.dayChange, 1e-9)
    }

    @Test
    fun `orders and holdings reference instruments from the same generated set`() {
        // The reason MarketData exists at all: three screens showing three different prices for
        // AAPL is the failure mode of generating per screen.
        val data = generate(17)
        val symbols = data.instruments.map(Instrument::symbol).toSet()
        assertTrue(data.orders.all { it.symbol in symbols })
        assertTrue(data.portfolio.holdings.all { it.instrument.symbol in symbols })
        assertTrue(data.news.all { article -> symbols.any { article.headline.startsWith("$it:") } })
    }

    @Test
    fun `news is ordered most recent first`() {
        val ages = generate(19).news.map(NewsArticle::minutesAgo)
        assertEquals(ages.sorted(), ages)
    }

    @Test
    fun `default favourites are non-empty so the dashboard widget has content`() {
        assertTrue(generate(23).defaultFavourites.isNotEmpty())
    }
}
