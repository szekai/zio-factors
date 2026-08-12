package io.github.szekai.ziofactors.examples

import zio.json.*
import io.github.szekai.ziofactors.alpha.Alpha158
import io.github.szekai.ziofactors.bar.Bar
import io.github.szekai.ziofactors.expr.Evaluator

/** CLI: compute the full Alpha158 feature set from a bars CSV.
  *
  * Input CSV columns (header row required, order fixed):
  *   `date,open,high,low,close,vwap,volume`
  *
  * Usage:
  *   sbt "examples/runMain io.github.szekai.ziofactors.examples.CsvAlpha158 \
  *        --csv examples/sample_data/bars.csv --out out.json --format json"
  *   sbt "examples/runMain io.github.szekai.ziofactors.examples.CsvAlpha158 \
  *        --csv examples/sample_data/bars.csv --out out.csv --format csv"
  *
  * `--names` optionally restricts to a comma-separated subset of factor names.
  */
object CsvAlpha158:

  final case class Output(dates: List[String], features: Map[String, List[Option[Double]]])
  object Output:
    given JsonCodec[Output] = DeriveJsonCodec.gen

  final case class Args(csv: String, out: String, format: String, names: Option[List[String]])

  private def parseArgs(argv: Array[String]): Args =
    def next(i: Int): String = if i + 1 < argv.length then argv(i + 1) else ""
    var csv = "examples/sample_data/bars.csv"
    var out = "out.json"
    var format = "json"
    var names: Option[List[String]] = None
    var i = 0
    while i < argv.length do
      argv(i) match
        case "--csv"    => csv = next(i); i += 1
        case "--out"    => out = next(i); i += 1
        case "--format" => format = next(i); i += 1
        case "--names"  => names = Some(next(i).split(",").toList.map(_.trim).filter(_.nonEmpty)); i += 1
        case other      => System.err.println(s"ignoring unknown argument: $other")
      i += 1
    Args(csv, out, format, names)

  private def parseBars(path: String): List[Bar] =
    val source = scala.io.Source.fromFile(resolveInput(path))
    try
      val lines = source.getLines().toList
      val body = lines.dropWhile(_.trim.isEmpty).drop(1) // skip header
      body.filter(_.trim.nonEmpty).map { line =>
        val p = line.split(",").map(_.trim)
        Bar(
          date = p(0),
          open = p(1).toDouble,
          high = p(2).toDouble,
          low = p(3).toDouble,
          close = p(4).toDouble,
          vwap = p(5).toDouble,
          volume = p(6).toDouble
        )
      }
    finally source.close()

  /** Forked sbt runs use the examples/ dir as CWD — resolve the path relative to
    * the project root, the examples/ dir, or as given.
    */
  private def resolveInput(p: String): String =
    val candidates = List(p, s"../$p", p.stripPrefix("examples/")).distinct
    candidates
      .find(c => java.nio.file.Files.exists(java.nio.file.Path.of(c)))
      .getOrElse(p)

  private def fmt(v: Double): String =
    if v.isNaN then "nan"
    else if v.isInfinite then (if v > 0 then "inf" else "-inf")
    else f"$v%.6f"

  private def toCsv(dates: List[String], selected: List[(String, List[Double])]): String =
    val header = ("date" :: selected.map(_._1)).mkString(",")
    val rows = dates.indices.map { i =>
      (dates(i) :: selected.map(_._2(i)).map(fmt)).mkString(",")
    }
    (header +: rows).mkString("\n")

  def main(argv: Array[String]): Unit =
    val args = parseArgs(argv)
    val bars = parseBars(args.csv)
    require(bars.nonEmpty, s"no bars parsed from ${args.csv}")

    val selectedFactors = args.names match
      case None => Alpha158.factors
      case Some(names) =>
        val byName = Alpha158.factors.map(f => f.name -> f).toMap
        names.flatMap(byName.get) match
          case found if found.size == names.size => found
          case found =>
            val missing = names.diff(found.map(_.name))
            sys.error(s"unknown factor names: ${missing.mkString(", ")}")

    val dates = bars.map(_.date)
    val series = selectedFactors.map(f => f.name -> Evaluator.seriesOf(f.expr, bars))
    val outPath = java.nio.file.Path.of(args.out).toAbsolutePath
    Option(outPath.getParent).foreach(java.nio.file.Files.createDirectories(_))

    args.format match
      case "csv" =>
        java.nio.file.Files.writeString(outPath, toCsv(dates, series))
      case "json" =>
        val json = Output(
          dates = dates,
          features = series.map { case (n, s) =>
            n -> s.map(v => if v.isNaN then None else Some(v))
          }.toMap
        ).toJson
        java.nio.file.Files.writeString(outPath, json)
      case other => sys.error(s"unknown --format $other (expected csv|json)")

    println(s"wrote ${series.size} features x ${bars.size} bars to $outPath")
    series.take(5).foreach { case (n, s) =>
      println(s"  $n: first=${fmt(s.head)} last=${fmt(s.last)}")
    }
