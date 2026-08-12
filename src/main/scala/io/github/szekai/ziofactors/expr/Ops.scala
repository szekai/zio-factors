package io.github.szekai.ziofactors.expr

/** Rolling operators with exact Qlib/pandas semantics.
  *
  * Every rolling helper is length-preserving and uses `min_periods = 1`: it emits
  * a value from the first bar, computed over the non-NaN elements inside the
  * window, and produces `NaN` only when there is nothing to compute (e.g. < 2
  * valid observations for `Std`, `Slope`, `Corr`).
  *
  * `Slope`/`Rsquare`/`Resi` replicate Qlib's Cython `_libs/rolling.pyx`: the
  * regression x-coordinate of an element is its 1-based slot position within the
  * window (newest = n), so NaN gaps widen the coordinate spacing — the regression
  * is against absolute window position, not compressed position.
  */
object Ops:

  /** Qlib nullifies Corr/Rsquare outputs where rolling std ≈ 0. */
  private val Atol = 2e-05

  def rollingMean(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n)(vals => vals.sum / vals.length)

  def rollingSum(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n)(_.sum)

  /** Sample std (ddof = 1, pandas default) — NaN for < 2 valid observations. */
  def rollingStd(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n) { vals =>
      if vals.length < 2 then Double.NaN
      else
        val m = vals.sum / vals.length
        val v = vals.map(x => (x - m) * (x - m)).sum / (vals.length - 1)
        math.sqrt(v)
    }

  def rollingMax(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n)(_.max)

  def rollingMin(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n)(_.min)

  /** Count of non-NaN elements in the window. */
  def rollingCount(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n)(_.length.toDouble)

  /** Linear-interpolation quantile over non-NaN elements (pandas default). */
  def rollingQuantile(s: List[Double], n: Int, q: Double): List[Double] =
    rollingAgg(s, n) { vals =>
      val sorted = vals.sorted
      if sorted.isEmpty then Double.NaN
      else
        val pos = q * (sorted.length - 1)
        val lo = math.floor(pos).toInt
        val hi = math.ceil(pos).toInt
        if lo == hi then sorted(lo)
        else sorted(lo) + (sorted(hi) - sorted(lo)) * (pos - lo)
    }

  /** Percentile rank (pct) of the last value within the window: average rank of
    * the value among non-NaN elements, divided by the count (pandas
    * `rolling.rank(pct=True)`). NaN when the last value is NaN.
    */
  def rollingRank(s: List[Double], n: Int): List[Double] =
    val arr = s.toArray
    val len = arr.length
    val out = new Array[Double](len)
    java.util.Arrays.fill(out, Double.NaN)
    var t = 0
    while t < len do
      val lo = math.max(0, t - n + 1)
      if !arr(t).isNaN then
        val valid = (lo to t).map(arr(_)).filter(v => !v.isNaN).toArray
        if valid.nonEmpty then
          val less = valid.count(_ < arr(t)).toDouble
          val eq = valid.count(_ == arr(t)).toDouble
          val avgRank = less + (eq + 1.0) / 2.0
          out(t) = avgRank / valid.length
      t += 1
    out.toList

  /** Position of the first window max (numpy `argmax` + 1), NaN for empty. */
  def rollingIdxMax(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n) { vals =>
      if vals.isEmpty then Double.NaN
      else {
        var best = 0
        var k = 1
        while k < vals.length do
          if vals(k) > vals(best) then best = k
          k += 1
        best + 1.0
      }
    }

  /** Position of the first window min (numpy `argmin` + 1), NaN for empty. */
  def rollingIdxMin(s: List[Double], n: Int): List[Double] =
    rollingAgg(s, n) { vals =>
      if vals.isEmpty then Double.NaN
      else {
        var best = 0
        var k = 1
        while k < vals.length do
          if vals(k) < vals(best) then best = k
          k += 1
        best + 1.0
      }
    }

  /** OLS slope of the window values vs 1-based slot position (newest = n).
    * Needs >= 2 valid observations; NaN otherwise.
    */
  def rollingSlope(s: List[Double], n: Int): List[Double] =
    rollingReg(s, n)(_.slope)

  /** R-squared of the same regression. Nullified where rolling std ≈ 0. */
  def rollingRsquare(s: List[Double], n: Int): List[Double] =
    val out = rollingReg(s, n)(_.rsquare)
    val sd = rollingStd(s, n)
    out.zip(sd).map { case (v, sdv) =>
      if sdv.isNaN then v
      else if math.abs(sdv) < Atol then Double.NaN
      else v
    }

  /** Last residual: `y_t - (slope * n + intercept)` (Cython `Resi`). */
  def rollingResi(s: List[Double], n: Int): List[Double] =
    rollingReg(s, n)(_.resi)

  /** Pearson correlation over aligned windows (pandas `rolling.corr` with
    * min_periods = 1, ddof = 1 internally). NaN when < 2 valid pairs or either
    * window has zero variance (nullified at `atol = 2e-05`, matching Qlib).
    */
  def rollingCorr(xs: List[Double], ys: List[Double], n: Int): List[Double] =
    val xa = xs.toArray
    val ya = ys.toArray
    val len = math.min(xa.length, ya.length)
    val out = new Array[Double](len)
    java.util.Arrays.fill(out, Double.NaN)
    var t = 0
    while t < len do
      val lo = math.max(0, t - n + 1)
      var sx = 0.0
      var sy = 0.0
      var sxx = 0.0
      var syy = 0.0
      var sxy = 0.0
      var cnt = 0
      var k = lo
      while k <= t do
        val x = xa(k)
        val y = ya(k)
        if !x.isNaN && !y.isNaN then
          sx += x
          sy += y
          sxx += x * x
          syy += y * y
          sxy += x * y
          cnt += 1
        k += 1
      if cnt >= 2 then
        val cov = sxy - sx * sy / cnt
        val varX = sxx - sx * sx / cnt
        val varY = syy - sy * sy / cnt
        val denom = math.sqrt(varX * varY)
        if varX.isNaN || varY.isNaN || denom <= 0.0 then out(t) = Double.NaN
        else out(t) = cov / denom
      t += 1
    // Qlib nullifies where either input's rolling std ≈ 0
    val sdx = rollingStd(xs, n)
    val sdy = rollingStd(ys, n)
    out.toList.zip(sdx).zip(sdy).map { case ((v, ax), ay) =>
      val xFlat = !ax.isNaN && math.abs(ax) < Atol
      val yFlat = !ay.isNaN && math.abs(ay) < Atol
      if xFlat || yFlat then Double.NaN else v
    }

  // --- shared window machinery ---

  private def rollingAgg(s: List[Double], n: Int)(
      f: Array[Double] => Double
  ): List[Double] =
    val arr = s.toArray
    val len = arr.length
    val out = new Array[Double](len)
    java.util.Arrays.fill(out, Double.NaN)
    var t = 0
    while t < len do
      val lo = math.max(0, t - n + 1)
      val vals = new Array[Double](t - lo + 1)
      var cnt = 0
      var k = lo
      while k <= t do
        if !arr(k).isNaN then
          vals(cnt) = arr(k)
          cnt += 1
        k += 1
      if cnt > 0 then out(t) = f(java.util.Arrays.copyOf(vals, cnt))
      t += 1
    out.toList

  /** Regression over valid elements. Slope/Rsquare use 1-based slot
    * x-coordinates (shift-invariant), but the residual fits at the newest slot
    * (x = n) using raw-index coordinates, exactly like the Cython `Resi`.
    */
  private def rollingReg(s: List[Double], n: Int)(
      f: RegResult => Double
  ): List[Double] =
    val arr = s.toArray
    val len = arr.length
    val out = new Array[Double](len)
    java.util.Arrays.fill(out, Double.NaN)
    var t = 0
    while t < len do
      val lo = math.max(0, t - n + 1)
      var sx = 0.0
      var sy = 0.0
      var sxx = 0.0
      var syy = 0.0
      var sxy = 0.0
      var sumIdx = 0.0
      var cnt = 0
      var k = lo
      while k <= t do
        val v = arr(k)
        if !v.isNaN then
          val x = (k - lo + 1).toDouble
          sx += x
          sy += v
          sxx += x * x
          syy += v * v
          sxy += x * v
          sumIdx += k
          cnt += 1
        k += 1
      if cnt >= 2 then
        val denom = cnt * sxx - sx * sx
        if denom != 0.0 then
          out(t) = f(RegResult(sx, sy, sxx, syy, sxy, cnt, sumIdx, t, arr(t)))
      t += 1
    out.toList

  private final case class RegResult(
      sx: Double,
      sy: Double,
      sxx: Double,
      syy: Double,
      sxy: Double,
      cnt: Int,
      sumIdx: Double,
      t: Int,
      last: Double
  ):
    def slope: Double = (cnt * sxy - sx * sy) / (cnt * sxx - sx * sx)

    def rsquare: Double =
      val r = (cnt * sxy - sx * sy) /
        math.sqrt((cnt * sxx - sx * sx) * (cnt * syy - sy * sy))
      r * r

    /** Cython: `last - (slope * n + (y_mean - slope * x_mean))` where the
      * newest slot coordinate is n and x_mean uses raw-index coords
      * `x_j = n - t + j`. Simplifies to `last - y_mean - slope * (t - sumIdx/cnt)`.
      */
    def resi: Double = last - (sy / cnt + slope * (t - sumIdx / cnt))
