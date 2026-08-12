package io.github.szekai.ziofactors.expr

/** Typed expression AST replicating the Qlib expression engine
  * (`qlib/data/ops.py`) operator-by-operator, restricted to the subset used by
  * the Alpha158 feature set plus a few convenience operators.
  *
  * Semantics mirror Qlib exactly:
  *   - `Ref(e, n)` = `series.shift(n)` — n > 0 looks n bars back (NaN head);
  *     n == 0 broadcasts the first value.
  *   - All rolling operators use `min_periods = 1` and ignore NaN elements
  *     inside the window (pandas semantics); they emit from the first bar.
  *   - `Std` is sample std (ddof = 1, pandas default) — NaN for < 2 valid.
  *   - `Div` is plain IEEE division — NO epsilon guard (Qlib relies on explicit
  *     `+ 1e-12` terms in expressions; `1/0 = Inf`, `0/0 = NaN`).
  *   - `Greater`/`Less` yield 1.0 / 0.0 (Qlib's `np.greater` bools coerce to 1.0
  *     when used in arithmetic; `Mean(bool, n)` = fraction, `Sum(bool, n)` = count).
  *   - `Corr` nullifies windows where either input's rolling std is ≈ 0
  *     (`np.isclose(..., atol = 2e-05)`).
  *
  * `lookback` mirrors Qlib's `get_longest_back_rolling`: the number of leading
  * bars that may be affected by window/ref context. The evaluator does not rely
  * on it for padding — NaN propagates naturally through the series.
  */
sealed trait Expr:
  def lookback: Int = Expr.lookbackOf(this)

object Expr:
  // --- leaves ---
  case object Close extends Expr
  case object Open extends Expr
  case object High extends Expr
  case object Low extends Expr
  case object Volume extends Expr
  case object Vwap extends Expr
  case class Number(value: Double) extends Expr

  // --- arithmetic ---
  case class Add(l: Expr, r: Expr) extends Expr
  case class Sub(l: Expr, r: Expr) extends Expr
  case class Mul(l: Expr, r: Expr) extends Expr
  case class Div(l: Expr, r: Expr) extends Expr
  case class Neg(e: Expr) extends Expr
  case class Max2(a: Expr, b: Expr) extends Expr
  case class Min2(a: Expr, b: Expr) extends Expr

  // --- comparisons (yield 1.0 / 0.0) ---
  case class Greater(a: Expr, b: Expr) extends Expr
  case class Less(a: Expr, b: Expr) extends Expr

  // --- unary functions ---
  case class Abs(e: Expr) extends Expr
  case class Log(e: Expr) extends Expr

  // --- shift ---
  case class Ref(e: Expr, n: Int) extends Expr

  // --- rolling (n >= 1; min_periods = 1; NaN elements skipped) ---
  case class Mean(e: Expr, n: Int) extends Expr
  case class Sum(e: Expr, n: Int) extends Expr
  case class Std(e: Expr, n: Int) extends Expr
  case class Max(e: Expr, n: Int) extends Expr
  case class Min(e: Expr, n: Int) extends Expr
  case class Quantile(e: Expr, n: Int, q: Double) extends Expr
  case class Rank(e: Expr, n: Int) extends Expr
  case class IdxMax(e: Expr, n: Int) extends Expr
  case class IdxMin(e: Expr, n: Int) extends Expr
  case class Count(e: Expr, n: Int) extends Expr
  case class Slope(e: Expr, n: Int) extends Expr
  case class Rsquare(e: Expr, n: Int) extends Expr
  case class Resi(e: Expr, n: Int) extends Expr

  // --- pair rolling ---
  case class Corr(x: Expr, y: Expr, n: Int) extends Expr

  // --- lookback metadata (mirrors Qlib get_longest_back_rolling) ---
  private def lookbackOf(e: Expr): Int = e match
    case Close | Open | High | Low | Volume | Vwap | Number(_) => 0
    case Add(l, r)      => math.max(l.lookback, r.lookback)
    case Sub(l, r)      => math.max(l.lookback, r.lookback)
    case Mul(l, r)      => math.max(l.lookback, r.lookback)
    case Div(l, r)      => math.max(l.lookback, r.lookback)
    case Neg(x)         => x.lookback
    case Max2(a, b)     => math.max(a.lookback, b.lookback)
    case Min2(a, b)     => math.max(a.lookback, b.lookback)
    case Greater(a, b)  => math.max(a.lookback, b.lookback)
    case Less(a, b)     => math.max(a.lookback, b.lookback)
    case Abs(x)         => x.lookback
    case Log(x)         => x.lookback
    case Ref(x, n)      => x.lookback + n
    case Mean(x, n)     => x.lookback + n - 1
    case Sum(x, n)      => x.lookback + n - 1
    case Std(x, n)      => x.lookback + n - 1
    case Max(x, n)      => x.lookback + n - 1
    case Min(x, n)      => x.lookback + n - 1
    case Quantile(x, n, _) => x.lookback + n - 1
    case Rank(x, n)     => x.lookback + n - 1
    case IdxMax(x, n)   => x.lookback + n - 1
    case IdxMin(x, n)   => x.lookback + n - 1
    case Count(x, n)    => x.lookback + n - 1
    case Slope(x, n)    => x.lookback + n - 1
    case Rsquare(x, n)  => x.lookback + n - 1
    case Resi(x, n)     => x.lookback + n - 1
    case Corr(x, y, n)  => math.max(x.lookback, y.lookback) + n - 1

  // --- DSL extensions ---
  extension (e: Expr)
    def +(o: Expr): Expr = Add(e, o)
    def -(o: Expr): Expr = Sub(e, o)
    def *(o: Expr): Expr = Mul(e, o)
    def /(o: Expr): Expr = Div(e, o)
    def unary_- : Expr = Neg(e)
    def ref(n: Int): Expr = Ref(e, n)
    def mean(n: Int): Expr = Mean(e, n)
    def sum(n: Int): Expr = Sum(e, n)
    def std(n: Int): Expr = Std(e, n)
    def max(n: Int): Expr = Max(e, n)
    def min(n: Int): Expr = Min(e, n)
    def quantile(n: Int, q: Double): Expr = Quantile(e, n, q)
    def rank(n: Int): Expr = Rank(e, n)
    def idxMax(n: Int): Expr = IdxMax(e, n)
    def idxMin(n: Int): Expr = IdxMin(e, n)
    def count(n: Int): Expr = Count(e, n)
    def slope(n: Int): Expr = Slope(e, n)
    def rsquare(n: Int): Expr = Rsquare(e, n)
    def resi(n: Int): Expr = Resi(e, n)
    def corr(other: Expr, n: Int): Expr = Corr(e, other, n)
    def greater(other: Expr): Expr = Greater(e, other)
    def less(other: Expr): Expr = Less(e, other)
    def abs: Expr = Abs(e)
    def log: Expr = Log(e)
