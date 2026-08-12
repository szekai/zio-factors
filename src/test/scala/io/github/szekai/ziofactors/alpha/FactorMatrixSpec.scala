package io.github.szekai.ziofactors.alpha

import zio.test.*
import zio.test.Assertion.*
import io.github.szekai.ziofactors.bar.Bar

object FactorMatrixSpec extends ZIOSpecDefault:

  private def mkBars(close: List[Double]): List[Bar] =
    close.indices.toList.map { i =>
      Bar(
        date = f"2024-01-${i + 1}%02d",
        open = close(i) - 0.5,
        high = close(i) + 1.0,
        low = close(i) - 1.0,
        close = close(i),
        vwap = close(i),
        volume = 1000.0
      )
    }

  private def single(name: String): Factor =
    Alpha158.factors.find(_.name == name).get

  val spec = suite("FactorMatrix")(
    test("rows = bars, cols = factors") {
      val bars = mkBars(List(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0))
      val m = FactorMatrix.matrix(bars, List(single("KMID"), single("ROC5")))
      assertTrue(
        m.length == 10,
        m.forall(row => row.length == 2)
      )
    },
    test("warmup NaN heads become 0.0; later rows match the raw series") {
      val bars = mkBars(List(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0))
      val m = FactorMatrix.matrix(bars, List(single("ROC5")))
      assertTrue(
        m.take(5).forall(row => row(0) == 0.0), // Ref(close,5) head -> NaN -> 0.0
        m(5)(0) == 10.0 / 15.0,                  // ROC5 = Ref(close,5)/close (past/current)
        m(9)(0) == 14.0 / 19.0
      )
    },
    test("KMID column matches Evaluator.seriesOf with NaN -> 0") {
      val bars = mkBars(List(100.0, 101.5, 100.2, 103.0, 102.1))
      val expected = {
        val s = io.github.szekai.ziofactors.expr.Evaluator.seriesOf(single("KMID").expr, bars)
        s.map(v => if v.isNaN || v.isInfinite then 0.0 else v)
      }
      val m = FactorMatrix.matrix(bars, List(single("KMID")))
      assert(m.map(_(0)))(equalTo(expected))
    },
    test("empty factors -> zero-width rows; empty bars -> empty matrix") {
      val bars = mkBars(List(1.0, 2.0))
      assertTrue(FactorMatrix.matrix(bars, Nil).forall(_.isEmpty))
      assertTrue(FactorMatrix.matrix(Nil, List(single("KMID"))).isEmpty)
    },
    test("NeuralSubset is 24 named factors, distinct, present in the 158") {
      assertTrue(
        Alpha158.NeuralSubset.size == 24,
        Alpha158.NeuralSubset.map(_.name).distinct.size == 24,
        Alpha158.NeuralSubset.forall(f => Alpha158.names.contains(f.name))
      )
    }
  )
