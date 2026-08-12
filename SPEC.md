# zio-factors — Qlib-parity technical factor library

**Project**: `/Users/szekai/Projects/scala/zio-factors` (sibling of `zio-nn`)
**Artifact**: `io.github.szekai:zio-factors_3:0.1.0` (Scala 3.8.4, sbt 2.0.1)
**Status**: 18/18 tests green (incl. 158-feature golden parity check)

A standalone, pure-Scala port of Microsoft Qlib's **Alpha158** feature set —
`Alpha158DL.get_feature_config` with the handler default config — with exact
operator semantics mirrored from `qlib/data/ops.py` and `qlib/data/_libs/rolling.pyx`.
Zero dependency on any marketwise model; consumers bring their own bar data
(`io.github.szekai.ziofactors.bar.Bar`).

## Parity sources (qlib main, 2026-08-12)

| Qlib file | What it defines |
|---|---|
| `qlib/contrib/data/loader.py` | `Alpha158DL.get_feature_config` — the 158 features (names + expressions) |
| `qlib/data/ops.py` | Operator semantics: `Ref` (shift), rolling ops with `min_periods = 1`, `Greater`/`Less` (1.0/0.0), `Log`, `Div` (plain IEEE), `Corr` (nullify zero-variance windows) |
| `qlib/data/_libs/rolling.pyx` | Cython `Slope` / `Rsquare` / `Resi` incremental regressions |

## Operator semantics (mirror exactly)

- **`Ref(e, n)`** = `series.shift(n)`: n > 0 → n-bar NaN head; n == 0 → broadcast first value.
- **Rolling ops** (`Mean`/`Sum`/`Std`/`Max`/`Min`/`Quantile`/`Rank`/`IdxMax`/`IdxMin`/`Count`) use **`min_periods = 1`**: emit from the first bar, computed over the non-NaN elements in the window; `Std` is **sample std (ddof = 1)** → NaN for < 2 valid.
- **`Div`** is plain IEEE division — `1/0 = Inf`, `0/0 = NaN`, NaN propagates. No epsilon guard (Qlib relies on explicit `+ 1e-12` terms in the expressions).
- **`Greater`/`Less`** yield 1.0 / 0.0 (Qlib's `np.greater` bools); `Mean(bool, n)` = fraction, `Sum(bool, n)` = count.
- **`Slope`/`Rsquare`/`Resi`** replicate the Cython regressions: x-coordinate = 1-based slot position (newest = n), NaN gaps widen the coordinate spacing; `Resi` fits at the newest slot using raw-index coordinates (`last − y_mean − slope·(t − Σj/N)`); `Rsquare`/`Corr` nullify windows whose rolling std ≈ 0 (`atol = 2e-05`).
- **`Corr`** = Pearson over pairwise-complete windows, NaN for < 2 pairs or zero variance.
- `lookback` mirrors Qlib's `get_longest_back_rolling` (metadata only — NaN propagates naturally, no artificial padding).

## Structure

`Alpha158.factors: List[Factor]` — **exactly 158**, in Qlib's generation order:

- **9 kbar**: KMID, KLEN, KMID2, KUP, KUP2, KLOW, KLOW2, KSFT, KSFT2
- **4 price**: OPEN0, HIGH0, LOW0, VWAP0
- **145 rolling**: 29 operators (ROC, MA, STD, BETA, RSQR, RESI, MAX, MIN, QTLU, QTLD,
  RANK, RSV, IMAX, IMIN, IMXD, CORR, CORD, CNTP, CNTN, CNTD, SUMP, SUMN, SUMD, VMA,
  VSTD, WVMA, VSUMP, VSUMN, VSUMD) × 5 windows (5, 10, 20, 30, 60)

Notable Qlib quirks preserved verbatim: kbar factors divide by `$open` (KMID2/KUP2/
KLOW2/KSFT2 divide by `high−low+1e-12`); KUP/KLOW subtract the **boolean**
`Greater(open, close)` / `Less(open, close)`; BETA is `Slope(close)/close` (trend
slope, not close-vs-volume beta); RSV uses `Min(low)`/`Max(high)`; SUMP is
`Σgains / Σ|moves|`; MA/VMA normalize by current close/volume; ROC is
`Ref(close,d)/close` (past over current, no −1). RSI does not exist in the current
Qlib Alpha158 set.

## Neural integration API

- `Alpha158.NeuralSubset: List[Factor]` — curated **24-factor** subset for model
  inputs (momentum/trend/volatility/correlation + sane kbar trio; excludes
  pathological members like KUP2's tiny denominators and position indices).
- `FactorMatrix.matrix(bars, factors): List[Array[Double]]` — dense per-bar rows,
  NaN/Inf → 0.0 (dense model-input convention). Marketwise's `sensing` module
  consumes this via `sensing.alpha.Alpha158Features` (DailyBar → Bar adapter with
  a (high+low+close)/3 VWAP proxy) and threads it through
  `TrendLSTMAnalyzer`/`TrendTransformer` behind `TRAINING_INCLUDE_ALPHA158`
  (default off; requires retraining when enabled).

## Verification

- `ExprSpec` (14 tests) — operator semantics with hand-verified values.
- `Alpha158Spec` (3 tests) — 158-count/name contract + **golden parity**: every
  feature compared against `src/test/resources/goldens.json` (80 synthetic bars),
  generated with pandas 3.0.5 / numpy 2.5.2 replicating `Alpha158DL` + `ops.py` +
  the literal Cython `rolling.pyx` port. The generator script lives outside the
  project (OS temp) — the project is pure Scala; the fixture is test data.

## Usage

```scala
import io.github.szekai.ziofactors.alpha.Alpha158
import io.github.szekai.ziofactors.bar.Bar

val bars: List[Bar] = ???              // your OHLCV+vwap history
val scores: Map[String, Double] = Alpha158.factors.map(f => f.name -> f.compute(bars)).toMap
```
