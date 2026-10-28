package com.funnelbase.demo.trading.data

import kotlin.random.Random

enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    FILLED("Filled"),
    CANCELLED("Cancelled"),
}

enum class OrderSide(val label: String) {
    BUY("Buy"),
    SELL("Sell"),
}

/**
 * One order.
 *
 * [cost] is computed, not stored, so quantity × price and the displayed total can never disagree
 * — which is exactly the kind of thing a reader checks with a calculator on a demo screen.
 */
data class Order(
    val id: String,
    val symbol: String,
    val companyName: String,
    val side: OrderSide,
    val status: OrderStatus,
    val quantity: Int,
    val price: Double,
    val placedMinutesAgo: Int,
) {
    val cost: Double get() = quantity * price
}

object Orders {

    /**
     * Sample orders across all three statuses.
     *
     * Prices come from the instruments generated for this launch rather than being invented, so
     * an order for AAPL sits in the same range as AAPL does on the Markets screen. Filled orders
     * are priced slightly off the current price — an order that filled earlier in the day at
     * exactly the current price would be a coincidence on every row.
     */
    fun generate(random: Random, instruments: List<Instrument>): List<Order> {
        val statuses = List(7) { OrderStatus.PENDING } +
            List(8) { OrderStatus.FILLED } +
            List(5) { OrderStatus.CANCELLED }

        return statuses.mapIndexed { index, status ->
            val instrument = instruments.random(random)
            val drift = when (status) {
                OrderStatus.FILLED -> random.nextDouble(-0.04, 0.04)
                // A resting limit order sits away from the market, which is why it is resting.
                OrderStatus.PENDING -> random.nextDouble(-0.08, 0.08)
                OrderStatus.CANCELLED -> random.nextDouble(-0.12, 0.12)
            }

            Order(
                id = "ORD-%05d".format(random.nextInt(10_000, 99_999)),
                symbol = instrument.symbol,
                companyName = instrument.name,
                side = if (random.nextBoolean()) OrderSide.BUY else OrderSide.SELL,
                status = status,
                quantity = QUANTITIES.random(random),
                price = Math.round(instrument.price * (1 + drift) * 100.0) / 100.0,
                placedMinutesAgo = (index + 1) * random.nextInt(7, 90),
            )
        }.sortedBy { it.placedMinutesAgo }
    }

    // Round lots, the way someone actually types an order — not nextInt(1, 500).
    private val QUANTITIES = listOf(5, 10, 15, 20, 25, 40, 50, 75, 100, 120, 150, 200, 250, 500)
}
