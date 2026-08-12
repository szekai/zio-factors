package io.github.szekai.ziofactors.expr

import io.github.szekai.ziofactors.bar.Bar
import Expr.*

/** Pure evaluation of an `Expr` over a bar series with Qlib-exact semantics.
  * `seriesOf` is length-preserving; NaN propagates naturally (Ref heads, IEEE
  * division, rolling degeneracies) — there is no artificial padding.
  */
object Evaluator:
  def seriesOf(e: Expr, bars: List[Bar]): List[Double] =
    e match
      case Close         => bars.map(_.close)
      case Open          => bars.map(_.open)
      case High          => bars.map(_.high)
      case Low           => bars.map(_.low)
      case Volume        => bars.map(_.volume)
      case Vwap          => bars.map(_.vwap)
      case Number(v)     => List.fill(bars.length)(v)
      case Add(l, r)     => zip(seriesOf(l, bars), seriesOf(r, bars))(_ + _)
      case Sub(l, r)     => zip(seriesOf(l, bars), seriesOf(r, bars))(_ - _)
      case Mul(l, r)     => zip(seriesOf(l, bars), seriesOf(r, bars))(_ * _)
      case Div(l, r)     => zip(seriesOf(l, bars), seriesOf(r, bars))(_ / _)
      case Neg(x)        => seriesOf(x, bars).map(-_)
      case Max2(a, b)    => zip(seriesOf(a, bars), seriesOf(b, bars))(math.max)
      case Min2(a, b)    => zip(seriesOf(a, bars), seriesOf(b, bars))(math.min)
      case Greater(a, b) =>
        zip(seriesOf(a, bars), seriesOf(b, bars))((x, y) => if x > y then 1.0 else 0.0)
      case Less(a, b) =>
        zip(seriesOf(a, bars), seriesOf(b, bars))((x, y) => if x < y then 1.0 else 0.0)
      case Abs(x) => seriesOf(x, bars).map(math.abs)
      case Log(x) => seriesOf(x, bars).map(math.log)
      case Ref(x, n) =>
        val s = seriesOf(x, bars)
        if n == 0 then
          s.headOption match
            case Some(v) => List.fill(s.length)(v)
            case None    => s
        else if n >= s.length then List.fill(s.length)(Double.NaN)
        else List.fill(n)(Double.NaN) ++ s.dropRight(n)
      case Mean(x, n)       => Ops.rollingMean(seriesOf(x, bars), n)
      case Sum(x, n)        => Ops.rollingSum(seriesOf(x, bars), n)
      case Std(x, n)        => Ops.rollingStd(seriesOf(x, bars), n)
      case Max(x, n)        => Ops.rollingMax(seriesOf(x, bars), n)
      case Min(x, n)        => Ops.rollingMin(seriesOf(x, bars), n)
      case Quantile(x, n, q) => Ops.rollingQuantile(seriesOf(x, bars), n, q)
      case Rank(x, n)       => Ops.rollingRank(seriesOf(x, bars), n)
      case IdxMax(x, n)     => Ops.rollingIdxMax(seriesOf(x, bars), n)
      case IdxMin(x, n)     => Ops.rollingIdxMin(seriesOf(x, bars), n)
      case Count(x, n)      => Ops.rollingCount(seriesOf(x, bars), n)
      case Slope(x, n)      => Ops.rollingSlope(seriesOf(x, bars), n)
      case Rsquare(x, n)    => Ops.rollingRsquare(seriesOf(x, bars), n)
      case Resi(x, n)       => Ops.rollingResi(seriesOf(x, bars), n)
      case Corr(x, y, n)    => Ops.rollingCorr(seriesOf(x, bars), seriesOf(y, bars), n)

  def lastValue(e: Expr, bars: List[Bar]): Double =
    seriesOf(e, bars).reverseIterator.find(v => !v.isNaN).getOrElse(Double.NaN)

  private def zip(a: List[Double], b: List[Double])(
      f: (Double, Double) => Double
  ): List[Double] =
    a.zip(b).map(f.tupled)
