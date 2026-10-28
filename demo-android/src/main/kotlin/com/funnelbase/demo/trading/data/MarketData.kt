package com.funnelbase.demo.trading.data

import kotlin.random.Random

/**
 * Everything the app displays, generated once per process.
 *
 * A single object rather than one generator per screen: the dashboard's portfolio, the orders
 * list and the news feed all reference instruments, and regenerating them per screen would show
 * three different prices for AAPL depending on which tab you were looking at.
 *
 * Deliberately not persisted and deliberately unseeded — a fresh random set every launch, which
 * is what makes it self-evident that none of it is real data.
 */
class MarketData private constructor(
    val instruments: List<Instrument>,
    val portfolio: Portfolio,
    val orders: List<Order>,
    val news: List<NewsArticle>,
) {
    private val bySymbol: Map<String, Instrument> = instruments.associateBy(Instrument::symbol)

    fun instrument(symbol: String): Instrument? = bySymbol[symbol]

    /** The symbols favourited by default on a first run, so the dashboard is never empty. */
    val defaultFavourites: Set<String> = instruments.take(4).map(Instrument::symbol).toSet()

    companion object {
        /**
         * Built lazily on first access rather than in `Application.onCreate` — generating fifty
         * instruments plus their lorem prose is not much work, but it is not work that belongs
         * on the path to the first frame either.
         */
        val current: MarketData by lazy { generate(Random.Default) }

        fun generate(random: Random): MarketData {
            val instruments = Instruments.generate(random)
            return MarketData(
                instruments = instruments,
                portfolio = Portfolios.generate(random, instruments),
                orders = Orders.generate(random, instruments),
                news = News.generate(random, instruments),
            )
        }
    }
}
