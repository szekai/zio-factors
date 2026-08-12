# zio-factors

**Qlib-parity technical factors for Scala 3 + ZIO.**

A standalone, pure-Scala port of Microsoft Qlib's **Alpha158** feature set —
`Alpha158DL.get_feature_config` with the handler default config — built for the
ZIO ecosystem. Every one of the **158 features** is verified against values
generated from Qlib's own expression semantics (`qlib/data/ops.py` +
`qlib/data/_libs/rolling.pyx`), so the library is a drop-in, bit-for-bit
equivalent of Qlib's feature layer.

- **Language / stack:** Scala 3.8.4 · sbt 2.0.1 · ZIO 2 (test framework) · zio-json
- **Artifact:** `io.github.szekai:zio-factors_3:0.1.0`
- **License:** Apache-2.0 · **Tests:** 18/18 (incl. 158-feature golden parity)

---

## Features

- **Typed expression DSL** — Qlib-style operators as a Scala ADT:
  leaves (`Close`/`Open`/`High`/`Low`/`Volume`/`Vwap`), arithmetic, comparisons,
  `Ref` shifts, rolling statistics, `Slope`/`Rsquare`/`Resi` regressions, `Corr`.
- **Exactly 158 named factors** (`Alpha158.factors`) in Qlib's generation order:
  9 kbar + 4 price + 145 rolling (29 operators × windows 5/10/20/30/60).
- **Exact Qlib semantics** — `min_periods = 1` rolling, sample std (ddof = 1),
  plain IEEE division, `Greater`/`Less` boolean coercion, zero-variance
  nullification — including Qlib's quirks (`$open` kbar denominators, `BETA` as
  `Slope(close)/close`, `SUMP` as gains/|moves|, no RSI).
- **Pure Scala** — no Python, no pandas, no DJL. Deterministic math only.
- **Golden-verified** — all 158 features compared against a pandas/numpy fixture
  generated from a literal port of Qlib's Cython `rolling.pyx`.

## Quick start

```bash
# 1. Build + install locally
sbt publishLocal

# 2. Add to your build.sbt
libraryDependencies += "io.github.szekai" %% "zio-factors" % "0.1.0"
```

```scala
import io.github.szekai.ziofactors.alpha.Alpha158
import io.github.szekai.ziofactors.bar.Bar
import io.github.szekai.ziofactors.expr.Evaluator

val bars: List[Bar] = ??? // your OHLCV+vwap history

// Full feature series per factor (length == bars.length, NaN for warmup/missing)
val kmid: List[Double] =
  Evaluator.seriesOf(Alpha158.factors.find(_.name == "KMID").get.expr, bars)

// Or: last valid value per factor
val scores: Map[String, Double] =
  Alpha158.factors.map(f => f.name -> f.compute(bars)).toMap
```

See [examples](examples/README.md) for runnable code.

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/ALPHA158.md](docs/ALPHA158.md) | The 158-factor reference — formulas, windows, Qlib quirks preserved |
| [docs/EXPR.md](docs/EXPR.md) | Expression DSL + operator semantics reference |
| [docs/PARITY.md](docs/PARITY.md) | How parity with Qlib is achieved and verified |
| [SPEC.md](SPEC.md) | Engineering spec: sources, semantics, verification |

## Examples

```bash
# Full Alpha158 feature matrix from a bars CSV -> CSV or JSON
sbt "examples/runMain io.github.szekai.ziofactors.examples.CsvAlpha158 \
     --csv examples/sample_data/bars.csv --out out.json"

# DSL walkthrough
sbt "examples/runMain io.github.szekai.ziofactors.examples.InteractiveExpr"
```

## Architecture

```
Bar (date/open/high/low/close/vwap/volume)
   │
   ▼
Expr (typed ADT: Ref/Mean/Std/Slope/Rsquare/Resi/Corr/... + DSL)
   │
   ▼
Evaluator.seriesOf(expr, bars): List[Double]   ← length-preserving, NaN for missing
   │
   ▼
Alpha158.factors: List[Factor(name, expr)]      ← exactly 158, Qlib names
```

Feature computation is a pure function of bars: `bars -> Map[name, List[Double]]`
via `Evaluator.seriesOf`, or `bars -> Double` via `Factor.compute` (last non-NaN).

## Relationship to Qlib and MarketWise

- **Qlib** (github.com/microsoft/qlib) is Microsoft's AI-oriented quant platform.
  This library ports its *feature layer* (Alpha158) — the deterministic inputs
  Qlib feeds its ML models. The rest of Qlib's stack (model zoo, backtest,
  workflow) is intentionally out of scope; the equivalent here is MarketWise's
  DJL/zio-nn neural pipeline ([marketwise](../investment)).
- **MarketWise** (`/Users/szekai/Projects/scala/investment`) depends on this
  library to feed Alpha158 features into its neural scoring pipeline.

## Test & verification

```bash
sbt test                 # 18 tests: operator semantics + 158-feature golden parity
sbt "Test / executeTests"
```

## License

Apache-2.0. Qlib is Copyright (c) Microsoft Corporation, MIT License — this
project re-implements its Alpha158 *feature definitions*; no Qlib code is copied.
