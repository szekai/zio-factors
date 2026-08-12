package io.github.szekai.ziofactors.expr

import zio.test.*
import io.github.szekai.ziofactors.bar.Bar
import Expr.*

object ExprSpec extends ZIOSpecDefault:

  private def bars(close: List[Double], volume: List[Double] = Nil): List[Bar] =
    val vols = if volume.isEmpty then List.fill(close.length)(1000.0) else volume
    close.indices.toList.map { i =>
      Bar(
        date = f"2024-01-${i + 1}%02d",
        open = close(i) - 0.5,
        high = close(i) + 1.0,
        low = close(i) - 1.0,
        close = close(i),
        vwap = close(i),
        volume = vols(i)
      )
    }

  private def approx(actual: Double, expected: Double, tol: Double = 1e-9): Boolean =
    if expected.isNaN then actual.isNaN
    else if expected.isInfinite then actual == expected
    else !actual.isNaN && math.abs(actual - expected) < tol

  private def assertSeries(actual: List[Double], expected: List[Double]): TestResult =
    assertTrue(
      actual.length == expected.length,
      actual.zip(expected).forall { case (a, e) => approx(a, e) }
    )

  val spec = suite("Expr semantics")(
    test("Ref shifts right by n with NaN head; Ref(e, 0) broadcasts the first value") {
      val b = bars(List(1.0, 2.0, 3.0, 4.0))
      assertSeries(Evaluator.seriesOf(Ref(Close, 2), b), List(Double.NaN, Double.NaN, 1.0, 2.0)) &&
      assertSeries(Evaluator.seriesOf(Ref(Close, 0), b), List(1.0, 1.0, 1.0, 1.0)) &&
      assertSeries(Evaluator.seriesOf(Ref(Close, 5), b), List(Double.NaN, Double.NaN, Double.NaN, Double.NaN))
    },
    test("rolling ops use min_periods=1 (emit from the first bar)") {
      val b = bars(List(1.0, 2.0, 3.0, 4.0))
      assertSeries(Evaluator.seriesOf(Mean(Close, 3), b), List(1.0, 1.5, 2.0, 3.0)) &&
      assertSeries(Evaluator.seriesOf(Sum(Close, 2), b), List(1.0, 3.0, 5.0, 7.0))
    },
    test("Std is sample std (ddof = 1): NaN for a single observation") {
      val b = bars(List(1.0, 2.0, 3.0))
      val res = Evaluator.seriesOf(Std(Close, 2), b)
      assertTrue(res(0).isNaN, approx(res(1), 0.7071067811865476), approx(res(2), 0.7071067811865476))
    },
    test("Div is plain IEEE division: x/0 = Inf, 0/0 = NaN, NaN propagates") {
      val b = bars(List(1.0, 2.0))
      assertSeries(Evaluator.seriesOf(Div(Number(1), Number(0)), b), List(Double.PositiveInfinity, Double.PositiveInfinity)) &&
      assertTrue(Evaluator.seriesOf(Div(Number(0), Number(0)), b).forall(_.isNaN)) &&
      assertTrue(Evaluator.seriesOf(Div(Number(1), Ref(Close, 9)), b).forall(_.isNaN))
    },
    test("Greater/Less coerce to 1.0/0.0 (NaN comparison is false -> 0.0)") {
      val b = bars(List(1.0, 2.0, 3.0, 4.0))
      assertSeries(Evaluator.seriesOf(Greater(Close, Number(2.5)), b), List(0.0, 0.0, 1.0, 1.0)) &&
      assertSeries(Evaluator.seriesOf(Less(Close, Number(2.5)), b), List(1.0, 1.0, 0.0, 0.0)) &&
      assertSeries(Evaluator.seriesOf(Greater(Ref(Close, 9), Number(0)), b), List(0.0, 0.0, 0.0, 0.0))
    },
    test("Abs and Log") {
      val b = bars(List(-1.0, 2.0))
      assertSeries(Evaluator.seriesOf(Abs(Close), b), List(1.0, 2.0)) &&
      assertSeries(Evaluator.seriesOf(Log(Close), b), List(Double.NaN, math.log(2.0)))
    },
    test("Quantile uses linear interpolation over the window") {
      val b = bars(List(1.0, 2.0, 3.0, 4.0))
      assertSeries(Evaluator.seriesOf(Quantile(Close, 4, 0.5), b), List(1.0, 1.5, 2.0, 2.5))
    },
    test("Rank is average rank / valid count (pandas pct)") {
      val b = bars(List(2.0, 1.0, 4.0, 3.0))
      assertSeries(Evaluator.seriesOf(Rank(Close, 3), b), List(1.0, 0.5, 1.0, 2.0 / 3.0))
    },
    test("IdxMax/IdxMin are argmax/argmin position + 1 within the window") {
      val b = bars(List(1.0, 3.0, 2.0, 4.0))
      assertSeries(Evaluator.seriesOf(IdxMax(Close, 3), b), List(1.0, 2.0, 2.0, 3.0)) &&
      assertSeries(Evaluator.seriesOf(IdxMin(Close, 3), b), List(1.0, 1.0, 1.0, 2.0))
    },
    test("Count counts non-NaN elements") {
      val b = bars(List(1.0, 2.0, 3.0, 4.0))
      assertSeries(Evaluator.seriesOf(Count(Close, 2), b), List(1.0, 2.0, 2.0, 2.0))
    },
    test("Slope of a linear series is its gradient (NaN until 2 valid)") {
      val b = bars(List(1.0, 3.0, 5.0, 7.0))
      assertSeries(Evaluator.seriesOf(Slope(Close, 3), b), List(Double.NaN, 2.0, 2.0, 2.0))
    },
    test("Resi of a linear series is zero; Rsquare is one") {
      val b = bars(List(1.0, 3.0, 5.0, 7.0))
      assertSeries(Evaluator.seriesOf(Resi(Close, 3), b), List(Double.NaN, 0.0, 0.0, 0.0)) &&
      assertSeries(Evaluator.seriesOf(Rsquare(Close, 3), b), List(Double.NaN, 1.0, 1.0, 1.0))
    },
    test("Corr of a series with itself is 1.0; with its negation is -1.0") {
      val b = bars(List(1.0, 2.0, 3.0, 4.0, 5.0))
      val same = Evaluator.seriesOf(Corr(Close, Close, 3), b)
      val neg = Evaluator.seriesOf(Corr(Close, Mul(Close, Number(-1)), 3), b)
      assertTrue(same.take(1).forall(_.isNaN), same.drop(1).forall(v => approx(v, 1.0)))
      assertTrue(neg.take(1).forall(_.isNaN), neg.drop(1).forall(v => approx(v, -1.0)))
    },
    test("lastValue returns the last non-NaN; NaN if none") {
      val b = bars(List(1.0, 3.0, 5.0, 7.0))
      assertTrue(approx(Evaluator.lastValue(Slope(Close, 3), b), 2.0))
      assertTrue(Evaluator.lastValue(Ref(Close, 99), b).isNaN)
    },
    test("lookback mirrors Qlib get_longest_back_rolling") {
      assertTrue(
        Ref(Close, 5).lookback == 5,
        Mean(Close, 5).lookback == 4,
        Corr(Close, Volume, 10).lookback == 9,
        Div(Close, Ref(Close, 1)).lookback == 1,
        Slope(Close, 20).lookback == 19,
        Quantile(Close, 30, 0.8).lookback == 29
      )
    }
  )
