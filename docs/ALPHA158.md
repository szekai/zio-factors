# Alpha158 Factor Reference

Exactly **158 factors** — a verbatim port of Qlib's
`Alpha158DL.get_feature_config` with the handler default config:
`kbar = {}`, `price.windows = [0]`, `feature = [OPEN, HIGH, LOW, VWAP]`,
`rolling = {}` (every operator, windows `[5, 10, 20, 30, 60]`).

| Group | Count | Members |
|-------|-------|---------|
| kbar | 9 | KMID, KLEN, KMID2, KUP, KUP2, KLOW, KLOW2, KSFT, KSFT2 |
| price | 4 | OPEN0, HIGH0, LOW0, VWAP0 |
| rolling | 145 | 29 operators × 5 windows |
| **total** | **158** | — |

## kbar (9)

Notation: `C` = close, `O` = open, `H` = high, `L` = low, `G(a,b)` = 1 if `a > b`
else 0 (Qlib's `Greater`), `Lt(a,b)` = 1 if `a < b` else 0 (Qlib's `Less`).

| Name | Formula (DSL) |
|------|---------------|
| KMID  | `(C − O) / O` |
| KLEN  | `(H − L) / O` |
| KMID2 | `(C − O) / (H − L + 1e-12)` |
| KUP   | `(H − G(O, C)) / O` |
| KUP2  | `(H − G(O, C)) / (H − L + 1e-12)` |
| KLOW  | `(Lt(O, C) − L) / O` |
| KLOW2 | `(Lt(O, C) − L) / (H − L + 1e-12)` |
| KSFT  | `(2·C − H − L) / O` |
| KSFT2 | `(2·C − H − L) / (H − L + 1e-12)` |

> **Quirks preserved from Qlib:** kbar divides by `$open` (not close); KUP/KLOW
> subtract the **boolean** `Greater(open, close)` / `Less(open, close)` (1.0/0.0),
> not `max(open, close)` — this is Qlib's literal implementation.

## price (4)

`OPEN0 = O/C`, `HIGH0 = H/C`, `LOW0 = L/C`, `VWAP0 = vwap/C`.

## rolling (29 operators × windows 5, 10, 20, 30, 60)

`V` = volume, `Log1p(x)` = `log(x + 1)`.

| Prefix | Formula (window `d`) | Notes |
|--------|----------------------|-------|
| ROC   | `Ref(C, d) / C` | past-over-current, **no −1** |
| MA    | `Mean(C, d) / C` | normalized by current close |
| STD   | `Std(C, d) / C` | sample std (ddof = 1) |
| BETA  | `Slope(C, d) / C` | **linear trend slope**, not close-vs-volume beta |
| RSQR  | `Rsquare(C, d)` | R² of trend regression |
| RESI  | `Resi(C, d) / C` | last residual of trend regression |
| MAX   | `Max(H, d) / C` | |
| MIN   | `Min(L, d) / C` | |
| QTLU  | `Quantile(C, d, 0.8) / C` | 80th percentile |
| QTLD  | `Quantile(C, d, 0.2) / C` | 20th percentile |
| RANK  | `Rank(C, d)` | percentile of current close in window |
| RSV   | `(C − Min(L, d)) / (Max(H, d) − Min(L, d) + 1e-12)` | uses **low/high**, not close |
| IMAX  | `IdxMax(H, d) / d` | days since window high (normalized) |
| IMIN  | `IdxMin(L, d) / d` | days since window low (normalized) |
| IMXD  | `(IdxMax(H, d) − IdxMin(L, d)) / d` | high-low timing spread |
| CORR  | `Corr(C, Log(V + 1), d)` | close ↔ log-volume correlation |
| CORD  | `Corr(C/Ref(C,1), Log(V/Ref(V,1) + 1), d)` | change-to-change correlation |
| CNTP  | `Mean(G(C, Ref(C, 1)), d)` | fraction of up days |
| CNTN  | `Mean(Lt(C, Ref(C, 1)), d)` | fraction of down days |
| CNTD  | `CNTP − CNTN` | up-minus-down fraction |
| SUMP  | `Sum(G(C − Ref(C,1), 0), d) / (Sum(Abs(C − Ref(C,1)), d) + 1e-12)` | gains ÷ total |move| |
| SUMN  | `Sum(G(Ref(C,1) − C, 0), d) / (Sum(Abs(C − Ref(C,1)), d) + 1e-12)` | losses ÷ total |move| |
| SUMD  | `(Sum(gains, d) − Sum(losses, d)) / (Sum(Abs(C − Ref(C,1)), d) + 1e-12)` | gain-loss spread |
| VMA   | `Mean(V, d) / (V + 1e-12)` | volume mean normalized |
| VSTD  | `Std(V, d) / (V + 1e-12)` | |
| WVMA  | `Std(Abs(C/Ref(C,1) − 1)·V, d) / (Mean(Abs(C/Ref(C,1) − 1)·V, d) + 1e-12)` | volume-weighted change volatility |
| VSUMP | `Sum(G(V − Ref(V,1), 0), d) / (Sum(Abs(V − Ref(V,1)), d) + 1e-12)` | |
| VSUMN | `Sum(G(Ref(V,1) − V, 0), d) / (Sum(Abs(V − Ref(V,1)), d) + 1e-12)` | |
| VSUMD | `(VSUMP numerator − VSUMN numerator) / (Sum(Abs(V − Ref(V,1)), d) + 1e-12)` | |

## Notable Qlib quirks (kept verbatim)

1. **No RSI** — the current Qlib Alpha158 set does not include RSI14 (it has no
   `RSI` operator in `ops.py`). The classic RSI from the original paper table is
   intentionally not added, to keep name-for-name parity with Qlib's code.
2. **`BETA` is `Slope(close)/close`** — a time-series trend slope, not the
   close-vs-log-volume regression beta of the original paper table.
3. **`ROC` = `Ref(close, d)/close`** — past over current, no `− 1`; values ≈ 1.0
   when flat.
4. **`RSV` uses `Min(low)`/`Max(high)`** extrema, not close.
5. **`SUMP`/`SUMN` are ratios** of cumulative gains to cumulative absolute
   movement, not means of positive changes.
6. **`MA`/`VMA`/`STD`/`BETA` divide by current close/volume** — a normalization
   choice, not the classic `close/ref(mean(close,n),1)` form.
7. **`+1e-12` guards** appear inside expressions (Qlib's style); `Div` itself is
   plain IEEE division (`1/0 = Inf`).

## In code

`Alpha158.factors: List[Factor]` (generation order above), `Alpha158.names`,
`Alpha158.Windows`. A factor is `Factor(name: String, expr: Expr, polarity: Option[Polarity])`.
