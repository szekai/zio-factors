package io.github.szekai.ziofactors.examples

import io.github.szekai.ziofactors.alpha.{Alpha158, Factor}
import io.github.szekai.ziofactors.bar.Bar
import io.github.szekai.ziofactors.expr.Evaluator
import io.github.szekai.ziofactors.expr.Expr.*
import io.github.szekai.ziofactors.expr.Expr

/** Walkthrough: build expressions with the DSL, evaluate them over sample bars,
  * and pick a named subset from the Alpha158 library.
  *
  * Usage:
  *   sbt "examples/runMain io.github.szekai.ziofactors.examples.InteractiveExpr"
  */
object InteractiveExpr:

  private def loadSample(path: String): List[Bar] =
    val candidates = List(path, s"../$path", path.stripPrefix("examples/")).distinct
    val resolved = candidates.find(p => java.nio.file.Files.exists(java.nio.file.Path.of(p))).getOrElse(path)
    val source = scala.io.Source.fromFile(resolved)
    try
      source
        .getLines()
        .drop(1)
        .filter(_.trim.nonEmpty)
        .map { line =>
          val p = line.split(",").map(_.trim)
          Bar(p(0), p(1).toDouble, p(2).toDouble, p(3).toDouble, p(4).toDouble, p(5).toDouble, p(6).toDouble)
        }
        .toList
    finally source.close()

  private def show(label: String, expr: Expr, bars: List[Bar]): Unit =
    val s = Evaluator.seriesOf(expr, bars)
    println(f"  $label%-28s last=${s.last}%.6f  warmup-lookback=${expr.lookback}%d")

  def main(argv: Array[String]): Unit =
    val bars = loadSample(
      argv.headOption.getOrElse("examples/sample_data/bars.csv")
    )

    println(s"loaded ${bars.size} bars (${bars.head.date} .. ${bars.last.date})")
    println()

    println("== custom expressions built with the DSL ==")
    val kmid = (Close - Open) / (High - Low) // intraday body position
    val roc6 = Close / Close.ref(6) - Number(1) // 6-bar rate of change (Qlib-style)
    val cntp = (Close.greater(Close.ref(1))).mean(10) // % up days in 10
    val macross = (Close.mean(5) - Close.mean(20)) / Close // MA5-MA20 spread
    show("KMID = (C-O)/(H-L)", kmid, bars)
    show("ROC6 = C/Ref(C,6)-1", roc6, bars)
    show("CNTP10 = Mean(C>Ref(C,1),10)", cntp, bars)
    show("MA spread (MA5-MA20)/C", macross, bars)
    println()

    println("== named Alpha158 factors ==")
    println(s"  Alpha158.factors.size = ${Alpha158.factors.size}")
    println(s"  windows               = ${Alpha158.Windows.mkString(", ")}")
    println(s"  first 9 names         = ${Alpha158.names.take(9).mkString(", ")}")
    println()

    println("== selecting an explicit named subset (no prefix tricks) ==")
    val byName = Alpha158.factors.map(f => f.name -> f).toMap
    val pick = List("KMID", "KLEN", "RSV20", "CORR10", "CNTP5", "BETA20", "SUMP60")
    val subset: List[Factor] = pick.map(byName)
    subset.foreach(f => show(f.name, f.expr, bars))
    println()

    println("== compute() = the last non-NaN value per factor ==")
    subset.foreach(f => println(f"  ${f.name}%-8s compute=${f.compute(bars)}%.6f"))
