# Expression DSL + Operator Semantics

`io.github.szekai.ziofactors.expr` — a typed ADT (`Expr`) with Qlib-exact
semantics, plus a small DSL for building expressions.

## Node types

| Category | Nodes |
|----------|-------|
| Leaves | `Close`, `Open`, `High`, `Low`, `Volume`, `Vwap`, `Number(v)` |
| Arithmetic | `Add`, `Sub`, `Mul`, `Div`, `Neg`, `Max2`, `Min2` |
| Comparisons | `Greater(a, b)`, `Less(a, b)` → 1.0 / 0.0 |
| Unary functions | `Abs(e)`, `Log(e)` |
| Shift | `Ref(e, n)` |
| Rolling (window `n ≥ 1`) | `Mean`, `Sum`, `Std`, `Max`, `Min`, `Quantile(e, n, q)`, `Rank`, `IdxMax`, `IdxMin`, `Count`, `Slope`, `Rsquare`, `Resi` |
| Pair rolling | `Corr(x, y, n)` |

## Semantics (mirror Qlib exactly)

- **`Ref(e, n)`** = `series.shift(n)`: n > 0 → n-bar NaN head (`Ref(e,1)` = previous
  bar); n == 0 → broadcast the first value.
- **Rolling ops use `min_periods = 1`**: a value is emitted from the first bar,
  computed over the **non-NaN elements** in the window. `NaN` only when there is
  nothing to compute.
- **`Std` is sample std (ddof = 1)** (pandas default) — `NaN` for < 2 valid
  observations. (This differs from the population std some libraries use.)
- **`Div` is plain IEEE division** — `1/0 = +Inf`, `0/0 = NaN`, `NaN` propagates.
  There is **no epsilon guard**; Qlib puts explicit `+ 1e-12` terms inside
  expressions instead.
- **`Greater`/`Less`** yield 1.0 / 0.0 (Qlib's `np.greater` bools coerce in
  arithmetic). `Mean(bool, n)` = fraction, `Sum(bool, n)` = count.
  A `NaN` comparison is false → 0.0.
- **`Slope`/`Rsquare`/`Resi`** replicate Qlib's Cython `rolling.pyx`: the
  regression x-coordinate is the 1-based slot position (newest = `n`), so NaN
  gaps widen the coordinate spacing; `Resi` fits at the newest slot using
  raw-index coordinates (`last − y_mean − slope·(t − Σj/N)`). All three need
  ≥ 2 valid observations.
- **`Rsquare`/`Corr` nullify** windows whose rolling std ≈ 0 (`atol = 2e-05`),
  matching Qlib's post-processing.
- **`Corr`** = Pearson correlation over pairwise-complete windows; `NaN` for
  < 2 valid pairs or zero variance.
- **`Rank`** = pandas `rolling.rank(pct=True)`: average rank / valid count.
- **`Quantile`** = linear-interpolation quantile (pandas default) over non-NaN
  values.
- **`IdxMax`/`IdxMin`** = `argmax`/`argmin` position + 1 within the window
  (1-based), `NaN` for empty.
- **`Count`** = number of non-NaN elements in the window.
- **`lookback`** mirrors Qlib's `get_longest_back_rolling` (window-size
  metadata). The evaluator does **not** rely on it for padding — `NaN` propagates
  naturally through the series, so `seriesOf` is always length-preserving.

## DSL

Extension methods on `Expr` (import `Expr.*`):

```scala
import io.github.szekai.ziofactors.expr.Expr.*
import io.github.szekai.ziofactors.expr.Expr

val maSpread: Expr = (Close.mean(5) - Close.mean(20)) / Close
val roc: Expr      = Close / Close.ref(6) - Number(1)      // Qlib-style ROC
val cntp: Expr     = Close.greater(Close.ref(1)).mean(10)  // % up days
val corr: Expr     = Close.corr(Volume.log, 10)
val beta: Expr     = Close.slope(20) / Close               // Qlib BETA
val rsv: Expr      = (Close - Low.min(9)) / ((High.max(9) - Low.min(9)) + Number(1e-12))
```

| Operator | Method |
|----------|--------|
| `Add/Sub/Mul/Div` | `+`, `-`, `*`, `/` |
| `Neg` | `unary_-` |
| `Ref` | `.ref(n)` |
| `Mean/Sum/Std/Max/Min` | `.mean(n)`, `.sum(n)`, `.std(n)`, `.max(n)`, `.min(n)` |
| `Quantile` | `.quantile(n, q)` |
| `Rank` | `.rank(n)` |
| `IdxMax/IdxMin` | `.idxMax(n)`, `.idxMin(n)` |
| `Count` | `.count(n)` |
| `Slope/Rsquare/Resi` | `.slope(n)`, `.rsquare(n)`, `.resi(n)` |
| `Corr` | `.corr(other, n)` |
| `Greater/Less` | `.greater(other)`, `.less(other)` |
| `Abs/Log` | `.abs`, `.log` |

## Evaluation

```scala
import io.github.szekai.ziofactors.expr.Evaluator

val series: List[Double] = Evaluator.seriesOf(expr, bars) // length == bars.length
val last: Double         = Evaluator.lastValue(expr, bars) // last non-NaN, else NaN
```
