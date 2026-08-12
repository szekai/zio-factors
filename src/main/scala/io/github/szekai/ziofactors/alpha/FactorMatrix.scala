package io.github.szekai.ziofactors.alpha

import io.github.szekai.ziofactors.bar.Bar
import io.github.szekai.ziofactors.expr.Evaluator

/** Dense per-bar feature matrices for feeding a model.
  * Rows = bars (input order preserved), columns = factors.
  * NaN/Inf → 0.0 so the matrix is a valid dense model input (same convention
  * as the marketwise neural pipeline's fundamentals handling).
  */
object FactorMatrix:
  def matrix(bars: List[Bar], factors: List[Factor]): List[Array[Double]] =
    if bars.isEmpty then Nil
    else if factors.isEmpty then List.fill(bars.length)(Array.emptyDoubleArray)
    else
      val cols = factors.map(f =>
        Evaluator.seriesOf(f.expr, bars).map(v => if v.isNaN || v.isInfinite then 0.0 else v)
      )
      bars.indices.toList.map(i => cols.map(_(i)).toArray)
