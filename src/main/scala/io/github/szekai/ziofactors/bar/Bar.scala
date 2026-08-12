package io.github.szekai.ziofactors.bar

/** A single daily bar, mirroring the fields Qlib's Alpha158 consumes.
  * All prices are `Double`; `volume` is widened to `Double` (pandas float64) so
  * every series used by the expression engine is a plain `List[Double]`.
  */
final case class Bar(
    date: String,
    open: Double,
    high: Double,
    low: Double,
    close: Double,
    vwap: Double,
    volume: Double
)
