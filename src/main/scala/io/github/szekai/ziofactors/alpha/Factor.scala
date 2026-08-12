package io.github.szekai.ziofactors.alpha

import io.github.szekai.ziofactors.bar.Bar
import io.github.szekai.ziofactors.expr.{Evaluator, Expr}

enum Polarity:
  case Positive, Negative

/** A named factor: a fixed `Expr` plus an optional downstream polarity hint.
  * `compute` returns the last non-NaN value of the series (NaN if none).
  */
final case class Factor(name: String, expr: Expr, polarity: Option[Polarity] = None):
  def compute(bars: List[Bar]): Double =
    Evaluator.lastValue(expr, bars)
