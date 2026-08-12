package io.github.szekai.ziofactors.alpha

import io.github.szekai.ziofactors.expr.Expr
import io.github.szekai.ziofactors.expr.Expr.*

/** The full Qlib Alpha158 feature set — a faithful port of
  * `Alpha158DL.get_feature_config` with the handler default config
  * (`kbar = {}`, `price.windows = [0]`, `feature = [OPEN, HIGH, LOW, VWAP]`,
  * `rolling = {}` → every operator with windows `[5, 10, 20, 30, 60]`).
  *
  * Exactly 158 features: 9 kbar + 4 price + 145 rolling (29 operators × 5
  * windows). Names and formulas match `qlib/contrib/data/loader.py` verbatim,
  * including Qlib's `+ 1e-12` denominator guards and its `Greater`/`Less`
  * boolean usage inside the kbar factors.
  */
object Alpha158:

  val Windows: List[Int] = List(5, 10, 20, 30, 60)

  private val kbar: List[(String, Expr)] = List(
    "KMID"  -> (Close - Open) / Open,
    "KLEN"  -> (High - Low) / Open,
    "KMID2" -> (Close - Open) / ((High - Low) + Number(1e-12)),
    "KUP"   -> (High - Greater(Open, Close)) / Open,
    "KUP2"  -> (High - Greater(Open, Close)) / ((High - Low) + Number(1e-12)),
    "KLOW"  -> (Less(Open, Close) - Low) / Open,
    "KLOW2" -> (Less(Open, Close) - Low) / ((High - Low) + Number(1e-12)),
    "KSFT"  -> ((Number(2) * Close) - High - Low) / Open,
    "KSFT2" -> ((Number(2) * Close) - High - Low) / ((High - Low) + Number(1e-12))
  )

  private val price: List[(String, Expr)] = List(
    "OPEN0" -> Open / Close,
    "HIGH0" -> High / Close,
    "LOW0"  -> Low / Close,
    "VWAP0" -> Vwap / Close
  )

  /** Each rolling operator: name prefix → expression factory over the window. */
  private val rollingOps: List[(String, Int => Expr)] = List(
    "ROC"   -> (d => Ref(Close, d) / Close),
    "MA"    -> (d => Mean(Close, d) / Close),
    "STD"   -> (d => Std(Close, d) / Close),
    "BETA"  -> (d => Slope(Close, d) / Close),
    "RSQR"  -> (d => Rsquare(Close, d)),
    "RESI"  -> (d => Resi(Close, d) / Close),
    "MAX"   -> (d => Max(High, d) / Close),
    "MIN"   -> (d => Min(Low, d) / Close),
    "QTLU"  -> (d => Quantile(Close, d, 0.8) / Close),
    "QTLD"  -> (d => Quantile(Close, d, 0.2) / Close),
    "RANK"  -> (d => Rank(Close, d)),
    "RSV"   -> (d => (Close - Min(Low, d)) / ((Max(High, d) - Min(Low, d)) + Number(1e-12))),
    "IMAX"  -> (d => IdxMax(High, d) / Number(d.toDouble)),
    "IMIN"  -> (d => IdxMin(Low, d) / Number(d.toDouble)),
    "IMXD"  -> (d => (IdxMax(High, d) - IdxMin(Low, d)) / Number(d.toDouble)),
    "CORR"  -> (d => Corr(Close, Log(Volume + Number(1)), d)),
    "CORD"  -> (d => Corr(Close / Ref(Close, 1), Log((Volume / Ref(Volume, 1)) + Number(1)), d)),
    "CNTP"  -> (d => Mean(Greater(Close, Ref(Close, 1)), d)),
    "CNTN"  -> (d => Mean(Less(Close, Ref(Close, 1)), d)),
    "CNTD"  -> (d => Mean(Greater(Close, Ref(Close, 1)), d) - Mean(Less(Close, Ref(Close, 1)), d)),
    "SUMP"  -> (d => Sum(Greater(Close - Ref(Close, 1), Number(0)), d) / (Sum(Abs(Close - Ref(Close, 1)), d) + Number(1e-12))),
    "SUMN"  -> (d => Sum(Greater(Ref(Close, 1) - Close, Number(0)), d) / (Sum(Abs(Close - Ref(Close, 1)), d) + Number(1e-12))),
    "SUMD"  -> (d => (Sum(Greater(Close - Ref(Close, 1), Number(0)), d) - Sum(Greater(Ref(Close, 1) - Close, Number(0)), d)) / (Sum(Abs(Close - Ref(Close, 1)), d) + Number(1e-12))),
    "VMA"   -> (d => Mean(Volume, d) / (Volume + Number(1e-12))),
    "VSTD"  -> (d => Std(Volume, d) / (Volume + Number(1e-12))),
    "WVMA"  -> (d => Std(Abs(Close / Ref(Close, 1) - Number(1)) * Volume, d) / (Mean(Abs(Close / Ref(Close, 1) - Number(1)) * Volume, d) + Number(1e-12))),
    "VSUMP" -> (d => Sum(Greater(Volume - Ref(Volume, 1), Number(0)), d) / (Sum(Abs(Volume - Ref(Volume, 1)), d) + Number(1e-12))),
    "VSUMN" -> (d => Sum(Greater(Ref(Volume, 1) - Volume, Number(0)), d) / (Sum(Abs(Volume - Ref(Volume, 1)), d) + Number(1e-12))),
    "VSUMD" -> (d => (Sum(Greater(Volume - Ref(Volume, 1), Number(0)), d) - Sum(Greater(Ref(Volume, 1) - Volume, Number(0)), d)) / (Sum(Abs(Volume - Ref(Volume, 1)), d) + Number(1e-12)))
  )

  private val rolling: List[(String, Expr)] =
    rollingOps.flatMap { case (prefix, f) => Windows.map(d => s"$prefix$d" -> f(d)) }

  /** All 158 factors in Qlib's generation order. */
  val factors: List[Factor] =
    (kbar ++ price ++ rolling).map { case (name, expr) => Factor(name, expr) }

  val names: List[String] = factors.map(_.name)

  private val byName: Map[String, Factor] = factors.map(f => f.name -> f).toMap

  /** Curated neural-input subset: 24 stable, warmup-friendly factors spanning
    * momentum (ROC/MA/VMA/CNTP/RSV/SUMP), trend (BETA/RSQR/RESI), volatility
    * (STD/VSTD) and correlation, plus the sane kbar trio. Excludes pathological
    * members (KUP2/KLOW2 tiny denominators, IMAX/IMIN position indices, 60-bar
    * lags) that make poor raw model inputs.
    */
  val NeuralSubset: List[Factor] = List(
    "KMID", "KLEN", "KSFT", "ROC5", "ROC10", "MA5", "MA10", "VMA5", "VMA10",
    "STD5", "STD10", "BETA5", "BETA10", "RSQR5", "RESI5", "CNTP5", "CNTN5",
    "RSV5", "RSV10", "SUMP5", "SUMP10", "CORR5", "CORR10", "VSTD5"
  ).map(name => byName(name))
