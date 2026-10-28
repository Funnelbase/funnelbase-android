package com.funnelbase.demo.trading.data

import kotlin.random.Random

/**
 * One tradable instrument, priced.
 *
 * [changePercent] is the day's move and [price] is already the post-move price, so
 * [previousClose] is derived rather than stored — one of the two would otherwise be free to
 * drift out of agreement with the other.
 */
data class Instrument(
    val symbol: String,
    val name: String,
    val sector: String,
    val price: Double,
    val changePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val open: Double,
    val volume: Long,
    val marketCapUsd: Double,
    val about: String,
) {
    val previousClose: Double get() = price / (1 + changePercent / 100)
    val changeAbsolute: Double get() = price - previousClose
    val isUp: Boolean get() = changePercent >= 0
}

/**
 * The demo's instrument universe: 50 S&P 500 constituents, priced randomly at every launch.
 *
 * Random per launch is the deliberate choice — the app is a demo of the analytics SDK, not of a
 * market data feed, and re-rolling the numbers each run makes it obvious that nothing here is
 * real. The *band* each price is drawn from is per-ticker though, so a share of Berkshire never
 * costs less than a share of Ford and the list stays plausible enough to read.
 */
object Instruments {

    /** symbol, company, sector, and the price band the launch draws from. */
    private val UNIVERSE = listOf(
        Ticker("AAPL", "Apple Inc.", "Technology", 180.0, 240.0),
        Ticker("MSFT", "Microsoft Corporation", "Technology", 380.0, 470.0),
        Ticker("NVDA", "NVIDIA Corporation", "Technology", 95.0, 145.0),
        Ticker("AMZN", "Amazon.com, Inc.", "Consumer Discretionary", 170.0, 230.0),
        Ticker("GOOGL", "Alphabet Inc.", "Communication Services", 140.0, 200.0),
        Ticker("META", "Meta Platforms, Inc.", "Communication Services", 440.0, 620.0),
        Ticker("TSLA", "Tesla, Inc.", "Consumer Discretionary", 180.0, 400.0),
        Ticker("BRK.B", "Berkshire Hathaway Inc.", "Financials", 400.0, 490.0),
        Ticker("JPM", "JPMorgan Chase & Co.", "Financials", 190.0, 260.0),
        Ticker("V", "Visa Inc.", "Financials", 260.0, 320.0),
        Ticker("UNH", "UnitedHealth Group Inc.", "Health Care", 450.0, 600.0),
        Ticker("XOM", "Exxon Mobil Corporation", "Energy", 100.0, 125.0),
        Ticker("JNJ", "Johnson & Johnson", "Health Care", 140.0, 170.0),
        Ticker("MA", "Mastercard Incorporated", "Financials", 420.0, 540.0),
        Ticker("PG", "Procter & Gamble Co.", "Consumer Staples", 150.0, 180.0),
        Ticker("HD", "The Home Depot, Inc.", "Consumer Discretionary", 320.0, 420.0),
        Ticker("COST", "Costco Wholesale Corporation", "Consumer Staples", 700.0, 990.0),
        Ticker("ABBV", "AbbVie Inc.", "Health Care", 160.0, 205.0),
        Ticker("MRK", "Merck & Co., Inc.", "Health Care", 95.0, 135.0),
        Ticker("AVGO", "Broadcom Inc.", "Technology", 130.0, 250.0),
        Ticker("LLY", "Eli Lilly and Company", "Health Care", 700.0, 960.0),
        Ticker("PEP", "PepsiCo, Inc.", "Consumer Staples", 140.0, 180.0),
        Ticker("KO", "The Coca-Cola Company", "Consumer Staples", 58.0, 74.0),
        Ticker("WMT", "Walmart Inc.", "Consumer Staples", 60.0, 100.0),
        Ticker("BAC", "Bank of America Corporation", "Financials", 34.0, 48.0),
        Ticker("CVX", "Chevron Corporation", "Energy", 135.0, 170.0),
        Ticker("ADBE", "Adobe Inc.", "Technology", 400.0, 580.0),
        Ticker("CRM", "Salesforce, Inc.", "Technology", 230.0, 360.0),
        Ticker("NFLX", "Netflix, Inc.", "Communication Services", 600.0, 950.0),
        Ticker("AMD", "Advanced Micro Devices, Inc.", "Technology", 110.0, 210.0),
        Ticker("INTC", "Intel Corporation", "Technology", 19.0, 45.0),
        Ticker("CSCO", "Cisco Systems, Inc.", "Technology", 46.0, 62.0),
        Ticker("ACN", "Accenture plc", "Technology", 300.0, 390.0),
        Ticker("MCD", "McDonald's Corporation", "Consumer Discretionary", 250.0, 320.0),
        Ticker("TMO", "Thermo Fisher Scientific Inc.", "Health Care", 480.0, 620.0),
        Ticker("ABT", "Abbott Laboratories", "Health Care", 100.0, 130.0),
        Ticker("DIS", "The Walt Disney Company", "Communication Services", 84.0, 120.0),
        Ticker("VZ", "Verizon Communications Inc.", "Communication Services", 38.0, 46.0),
        Ticker("INTU", "Intuit Inc.", "Technology", 550.0, 720.0),
        Ticker("QCOM", "QUALCOMM Incorporated", "Technology", 145.0, 220.0),
        Ticker("TXN", "Texas Instruments Incorporated", "Technology", 160.0, 210.0),
        Ticker("NKE", "NIKE, Inc.", "Consumer Discretionary", 60.0, 100.0),
        Ticker("PFE", "Pfizer Inc.", "Health Care", 23.0, 32.0),
        Ticker("CAT", "Caterpillar Inc.", "Industrials", 300.0, 420.0),
        Ticker("GE", "GE Aerospace", "Industrials", 150.0, 210.0),
        Ticker("BA", "The Boeing Company", "Industrials", 130.0, 200.0),
        Ticker("UPS", "United Parcel Service, Inc.", "Industrials", 95.0, 145.0),
        Ticker("HON", "Honeywell International Inc.", "Industrials", 190.0, 230.0),
        Ticker("GS", "The Goldman Sachs Group, Inc.", "Financials", 420.0, 610.0),
        Ticker("F", "Ford Motor Company", "Consumer Discretionary", 9.0, 14.0),
    )

    /** Every instrument, priced for this launch, in the order shown on the Markets screen. */
    fun generate(random: Random): List<Instrument> = UNIVERSE.map { it.priced(random) }

    private data class Ticker(
        val symbol: String,
        val name: String,
        val sector: String,
        val low: Double,
        val high: Double,
    ) {
        fun priced(random: Random): Instrument {
            val price = round2(random.nextDouble(low, high))
            // ±5%, as a day's move. Bounded rather than normal: a demo list where one row shows
            // -40% reads as a bug rather than as a market.
            val changePercent = round2(random.nextDouble(-5.0, 5.0))
            val previousClose = price / (1 + changePercent / 100)

            // The day's range must actually contain both ends of the day, or the detail screen
            // shows a high below the current price.
            val spread = price * random.nextDouble(0.005, 0.03)
            val dayHigh = round2(maxOf(price, previousClose) + spread)
            val dayLow = round2(minOf(price, previousClose) - spread)
            val open = round2(random.nextDouble(dayLow, dayHigh))

            return Instrument(
                symbol = symbol,
                name = name,
                sector = sector,
                price = price,
                changePercent = changePercent,
                dayHigh = dayHigh,
                dayLow = dayLow,
                open = open,
                volume = random.nextLong(2_000_000, 90_000_000),
                marketCapUsd = price * random.nextLong(400_000_000, 9_000_000_000),
                about = buildString {
                    append(name)
                    append(" operates in the ")
                    append(sector.lowercase())
                    append(" sector. ")
                    append(Lorem.paragraph(random, sentences = 4))
                },
            )
        }
    }

    // No abs() here. It was tempting, since every price is positive — but changePercent goes
    // through the same helper, and stripping its sign made every instrument in the list green.
    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
