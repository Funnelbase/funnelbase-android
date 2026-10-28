package com.funnelbase.demo.trading.data

import kotlin.random.Random

data class Holding(
    val instrument: Instrument,
    val quantity: Int,
) {
    val marketValue: Double get() = quantity * instrument.price
    val dayChange: Double get() = quantity * instrument.changeAbsolute
}

/**
 * The signed-in user's positions.
 *
 * Day change is summed from the holdings rather than generated, so the percentage on the
 * dashboard is the one the Markets screen implies. A separately-invented portfolio number would
 * contradict the instrument list the moment anyone added it up.
 */
data class Portfolio(
    val holdings: List<Holding>,
    val cashUsd: Double,
) {
    val investedValue: Double get() = holdings.sumOf(Holding::marketValue)
    val totalValue: Double get() = investedValue + cashUsd
    val dayChange: Double get() = holdings.sumOf(Holding::dayChange)

    /** Against yesterday's close of the same positions; cash does not move, so it is excluded. */
    val dayChangePercent: Double
        get() {
            val previous = investedValue - dayChange
            return if (previous <= 0) 0.0 else dayChange / previous * 100
        }

    val isUp: Boolean get() = dayChange >= 0
}

object Portfolios {

    fun generate(random: Random, instruments: List<Instrument>): Portfolio {
        val held = instruments.shuffled(random).take(random.nextInt(5, 9))
        return Portfolio(
            holdings = held.map { Holding(it, quantity = QUANTITIES.random(random)) },
            cashUsd = Math.round(random.nextDouble(1_500.0, 25_000.0) * 100.0) / 100.0,
        )
    }

    private val QUANTITIES = listOf(4, 8, 12, 15, 20, 30, 45, 60, 80, 120)
}
