package io.github.szekai.ziofactors.alpha

import zio.json.*
import zio.test.*
import zio.test.Assertion.*
import io.github.szekai.ziofactors.bar.Bar
import io.github.szekai.ziofactors.expr.{Evaluator, Expr}

object Alpha158Spec extends ZIOSpecDefault:

  final case class GoldenBars(
      open: List[Double],
      high: List[Double],
      low: List[Double],
      close: List[Double],
      volume: List[Double],
      vwap: List[Double]
  )
  object GoldenBars:
    given JsonCodec[GoldenBars] = DeriveJsonCodec.gen

  final case class GoldenFixture(n: Int, bars: GoldenBars, features: Map[String, List[Option[Double]]])
  object GoldenFixture:
    given JsonCodec[GoldenFixture] = DeriveJsonCodec.gen

  private def goldens: GoldenFixture =
    val src = scala.io.Source.fromResource("goldens.json")
    try src.mkString.fromJson[GoldenFixture].fold(e => throw new RuntimeException(e), identity)
    finally src.close()

  private def toBars(g: GoldenBars): List[Bar] =
    g.close.indices.toList.map { i =>
      Bar(
        date = f"2024-01-${i + 1}%02d",
        open = g.open(i),
        high = g.high(i),
        low = g.low(i),
        close = g.close(i),
        vwap = g.vwap(i),
        volume = g.volume(i)
      )
    }

  private def closeEnough(a: Double, e: Double): Boolean =
    if e.isNaN then a.isNaN
    else math.abs(a - e) <= 1e-6 + 1e-5 * math.abs(e)

  private val kbarNames = List("KMID", "KLEN", "KMID2", "KUP", "KUP2", "KLOW", "KLOW2", "KSFT", "KSFT2")
  private val priceNames = List("OPEN0", "HIGH0", "LOW0", "VWAP0")
  private val rollingPrefixes = List(
    "ROC", "MA", "STD", "BETA", "RSQR", "RESI", "MAX", "MIN", "QTLU", "QTLD",
    "RANK", "RSV", "IMAX", "IMIN", "IMXD", "CORR", "CORD", "CNTP", "CNTN", "CNTD",
    "SUMP", "SUMN", "SUMD", "VMA", "VSTD", "WVMA", "VSUMP", "VSUMN", "VSUMD"
  )
  private val expectedNames =
    kbarNames ++ priceNames ++ rollingPrefixes.flatMap(p => Alpha158.Windows.map(d => s"$p$d"))

  val spec = suite("Alpha158")(
    test("contains exactly 158 factors with distinct, qlib-matching names") {
      assertTrue(
        Alpha158.factors.size == 158,
        Alpha158.names.distinct.size == 158,
        Alpha158.names == expectedNames
      )
    },
    test("every factor matches the pandas/qlib golden values (80 bars)") {
      val g = goldens
      val bars = toBars(g.bars)
      val byName = Alpha158.factors.map(f => f.name -> f.expr).toMap
      val mismatches = g.features.flatMap { case (name, expected) =>
        byName.get(name) match
          case None => Some(s"$name: missing from Alpha158")
          case Some(expr) =>
            val actual = Evaluator.seriesOf(expr, bars)
            val expectedArr = expected.map(_.getOrElse(Double.NaN)).toArray
            val bad = actual.zipWithIndex.find { case (a, i) => !closeEnough(a, expectedArr(i)) }
            bad.map { case (a, i) => f"$name[$i]: got $a%.6g expected ${expectedArr(i)}%.6g" }
      }
      assert(mismatches)(isEmpty)
    },
    test("compute returns the last non-NaN value") {
      val g = goldens
      val bars = toBars(g.bars)
      val last = g.features.map { case (name, vals) =>
        val expected = vals.reverseIterator.flatMap(_.toList).nextOption().getOrElse(Double.NaN)
        val actual = Alpha158.factors.find(_.name == name).get.compute(bars)
        name -> (actual, expected)
      }
      val bad = last.collect { case (name, (a, e)) if !closeEnough(a, e) => name }
      assert(bad)(isEmpty)
    }
  )
