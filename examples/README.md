# Examples

Run these from the project root (they load `examples/sample_data/bars.csv` — 40 bars
extracted from the same dataset the golden tests use).

## 1. Compute the full Alpha158 feature set from a bars CSV

```bash
# CSV output (date + 158 feature columns)
sbt "examples/runMain io.github.szekai.ziofactors.examples.CsvAlpha158 \
     --csv examples/sample_data/bars.csv --out out.csv --format csv"

# JSON output (dates + per-feature series, NaN encoded as null)
sbt "examples/runMain io.github.szekai.ziofactors.examples.CsvAlpha158 \
     --csv examples/sample_data/bars.csv --out out.json --format json"

# Restrict to a named subset
sbt "examples/runMain io.github.szekai.ziofactors.examples.CsvAlpha158 \
     --csv examples/sample_data/bars.csv --out subset.json \
     --names KMID,KLEN,RSV20,CORR10,CNTP5,BETA20,SUMP60"
```

Input CSV format (header required, order fixed):

```csv
date,open,high,low,close,vwap,volume
2024-01-01,100.789726,101.019656,99.891045,100.515660,101.021680,395372.0
...
```

## 2. DSL walkthrough

```bash
sbt "examples/runMain io.github.szekai.ziofactors.examples.InteractiveExpr"
```

Builds custom expressions with the DSL, evaluates them, and demonstrates selecting
an explicit named subset from `Alpha158.factors`:

```
== custom expressions built with the DSL ==
  KMID = (C-O)/(H-L)          last=0.086437  warmup-lookback=0
  ROC6 = C/Ref(C,6)-1         last=-0.038762  warmup-lookback=6
  CNTP10 = Mean(C>Ref(C,1),10) last=0.500000  warmup-lookback=9
  MA spread (MA5-MA20)/C      last=0.017584  warmup-lookback=19
...
```

## Input data

`sample_data/bars.csv` is a deterministic 40-bar synthetic series (OHLCV + VWAP)
generated with a fixed seed — the same bars the 158-feature golden parity test
(`Alpha158Spec`) validates against. Swap in your own CSV to run on real data.
