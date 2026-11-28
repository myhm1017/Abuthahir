#!/usr/bin/env python3
"""
Gold CRT robot backtest: Candle Range Theory + ICT structure shift + session timing.

What is taken from each method (only the parts that can be calculated exactly):
  CRT   The previous 4-hour candle's high/low is the range. Trades start when the current
        4H candle sweeps one side; the target is the opposite side.
  ICT   After the sweep, wait for a market structure shift (MSS) on 5-min: a candle that
        CLOSES beyond the low (for shorts) / high (for longs) of the --mss-lookback candles
        before the sweep extreme. Stop beyond the sweep wick.
  MSNR  Close-based confirmation: the MSS candle must close back inside the range
        (body close, not just a wick).
  QT    Timing: 4H candles are aligned to New York time (02, 06, 10, 14, 18, 22), so the
        02-06 candle sweeps the Asian range in London and the 06-10 candle sweeps London
        at the New York open. Sweep and MSS must happen inside a kill zone
        (London 02:00-05:00, New York 07:00-11:00 ET).

Trade:
  Entry   limit at --retrace of the leg from the sweep extreme to the MSS close (0.5 = middle;
          0 = market at the next candle's open). Expires after --expiry candles.
  Stop    sweep extreme +/- --buffer.
  Target  opposite side of the previous 4H candle. Optional: half off at the range midpoint
          and stop to breakeven (--partial). Skips setups under --min-rr.
  Exit    anything still open after --max-hold hours is closed. One trade per 4H candle.

Usage:
  python3 crt_backtest.py xauusd_1m.csv --tz UTC --start 2025-11-28 --end 2026-11-27
"""
import argparse
import os
import sys

import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from orb_backtest import bar_minutes, load_bars, stats, to_5m  # noqa: E402

KILLZONES = [("02:00", "05:00"), ("07:00", "11:00")]


def in_killzone(t):
    hm = t.strftime("%H:%M")
    return any(a <= hm < b for a, b in KILLZONES)


def to_4h(m5):
    return m5.resample("4h", offset="2h").agg(
        {"open": "first", "high": "max", "low": "min", "close": "last"}).dropna()


def find_setup(c5, ph, pl, lookback, start_hist):
    """Scan the current 4H candle's 5-min bars. Returns dict or None."""
    swept, extreme, ext_i = None, None, None
    rows = list(c5.itertuples())
    for i, b in enumerate(rows):
        t = b.Index
        if swept is None:
            if not in_killzone(t):
                continue
            hi, lo = b.high > ph, b.low < pl
            if hi and lo:
                return None                      # outside candle: no clean sweep
            if hi:
                swept, extreme, ext_i = "high", b.high, i
            elif lo:
                swept, extreme, ext_i = "low", b.low, i
            continue
        if (swept == "high" and b.low < pl) or (swept == "low" and b.high > ph):
            return None                          # took both sides: range broken
        if swept == "high" and b.high > extreme:
            extreme, ext_i = b.high, i
        if swept == "low" and b.low < extreme:
            extreme, ext_i = b.low, i
        if not in_killzone(t):
            continue
        # structure before the sweep extreme (may reach back into the previous candle)
        prior = start_hist[start_hist.index < rows[ext_i].Index].iloc[-lookback:]
        if len(prior) < lookback:
            continue
        if swept == "high":
            level = prior["low"].min()
            if b.close < level and b.close < ph:
                return dict(side="short", extreme=extreme, mss_close=b.close, mss_time=t, level=level)
        else:
            level = prior["high"].max()
            if b.close > level and b.close > pl:
                return dict(side="long", extreme=extreme, mss_close=b.close, mss_time=t, level=level)
    return None


def simulate(base, side, entry, stop, target, mid, start, fill_until, hold_until, partial, market):
    """Returns (filled, R_gross, reason, exit_time)."""
    sgn = 1 if side == "long" else -1
    risk = abs(entry - stop)
    bars = base[(base.index >= start) & (base.index < hold_until)]
    filled, size, realized, sl, took_partial = market, 1.0, 0.0, stop, False
    if market and len(bars):
        entry = bars.iloc[0]["open"]
        risk = abs(entry - stop)
        if risk <= 0 or (target - entry) * sgn <= 0:
            return False, 0.0, "invalid at open", start
    for t, b in bars.iterrows():
        hi, lo = b["high"], b["low"]
        if not filled:
            if t >= fill_until:
                return False, 0.0, "not filled", t
            if (lo <= entry) if side == "long" else (hi >= entry):
                filled = True
                if (lo <= sl) if side == "long" else (hi >= sl):
                    return True, -1.0, "stop", t
            continue
        if (lo <= sl) if side == "long" else (hi >= sl):
            realized += size * sgn * (sl - entry)
            return True, realized / risk, "stop" if not took_partial else "breakeven", t
        if partial and not took_partial and ((hi >= mid) if side == "long" else (lo <= mid)):
            realized += 0.5 * sgn * (mid - entry)
            size, sl, took_partial = 0.5, entry, True
        if (hi >= target) if side == "long" else (lo <= target):
            realized += size * sgn * (target - entry)
            return True, realized / risk, "target", t
    if not filled:
        return False, 0.0, "not filled", hold_until
    last = bars.iloc[-1]["close"]
    realized += size * sgn * (last - entry)
    return True, realized / risk, "time exit", bars.index[-1]


def run(df, start=None, end=None, lookback=6, retrace=0.5, expiry=6, buffer=0.10, min_rr=1.5,
        min_risk=1.0, partial=True, max_hold=8.0, cost=0.30):
    base = df
    m5 = to_5m(df) if bar_minutes(df) < 5 else df
    h4 = to_4h(m5)
    rows = []
    for k in range(1, len(h4)):
        c_t, p = h4.index[k], h4.iloc[k - 1]
        if h4.index[k] - h4.index[k - 1] != pd.Timedelta(hours=4):
            continue                              # weekend / gap: previous candle not adjacent
        day = str(c_t.date())
        if (start and day < start) or (end and day > end):
            continue
        c5 = m5[(m5.index >= c_t) & (m5.index < c_t + pd.Timedelta(hours=4))]
        if len(c5) < 30:
            continue
        ph, pl = p["high"], p["low"]
        s = find_setup(c5, ph, pl, lookback, m5[m5.index < c_t + pd.Timedelta(hours=4)])
        if s is None:
            continue
        side = s["side"]
        stop = s["extreme"] + buffer if side == "short" else s["extreme"] - buffer
        target = pl if side == "short" else ph
        mid = (ph + pl) / 2
        if retrace > 0:
            entry = s["mss_close"] + retrace * (s["extreme"] - s["mss_close"])
        else:
            entry = s["mss_close"]               # estimate for filters; real fill = next open
        risk = abs(entry - stop)
        reward = (entry - target) if side == "short" else (target - entry)
        rec = dict(date=day, candle=c_t.strftime("%H:%M"), side=side, range_high=round(ph, 2),
                   range_low=round(pl, 2), mss_time=s["mss_time"].strftime("%H:%M"),
                   entry=round(entry, 2), stop=round(stop, 2), target=round(target, 2),
                   risk=round(risk, 2), planned_R=round(reward / risk, 2) if risk > 0 else None)
        if risk < min_risk:
            rec["result"] = "stop too tight"
        elif reward < min_rr * risk:
            rec["result"] = "target too close"
        else:
            st = s["mss_time"] + pd.Timedelta(minutes=5)
            filled, r, why, xt = simulate(
                base, side, entry, stop, target, mid, st, st + pd.Timedelta(minutes=5 * expiry),
                st + pd.Timedelta(hours=max_hold), partial, retrace <= 0)
            rec["result"] = why
            if filled and why != "invalid at open":
                rec.update(exit_time=xt.strftime("%m-%d %H:%M"), R=round(r - cost / risk, 3))
        rows.append(rec)
    return pd.DataFrame(rows)


def report(tr):
    if tr.empty:
        return "No setups found."
    out = ["Setups: " + ", ".join(f"{k}: {v}" for k, v in tr["result"].value_counts().items())]
    t = tr.dropna(subset=["R"]) if "R" in tr else tr.iloc[0:0]
    if t.empty:
        return out[0] + "\nNo filled trades."
    out += ["", "== All trades ==", pd.Series(stats(t["R"])).to_string()]
    for name, grp in [("London (02:00 candle)", t[t.candle == "02:00"]),
                      ("New York (06:00 candle)", t[t.candle == "06:00"]),
                      ("long", t[t.side == "long"]), ("short", t[t.side == "short"])]:
        if len(grp):
            out += ["", f"== {name} ==", pd.Series(stats(grp["R"])).to_string()]
    m = t.copy()
    m["month"] = m["date"].str[:7]
    out += ["", "== R by month ==", m.groupby("month")["R"].agg(["count", "sum"]).round(2).to_string()]
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv")
    ap.add_argument("--tz", default="UTC")
    ap.add_argument("--start"), ap.add_argument("--end")
    ap.add_argument("--mss-lookback", type=int, default=6, help="5-min candles before the sweep that define structure")
    ap.add_argument("--retrace", type=float, default=0.5, help="entry retrace of sweep->MSS leg (0 = market)")
    ap.add_argument("--expiry", type=int, default=6, help="limit order lifetime in 5-min candles")
    ap.add_argument("--buffer", type=float, default=0.10)
    ap.add_argument("--min-rr", type=float, default=1.5)
    ap.add_argument("--min-risk", type=float, default=1.0)
    ap.add_argument("--no-partial", action="store_true", help="no half-off at the range midpoint")
    ap.add_argument("--max-hold", type=float, default=8.0, help="hours")
    ap.add_argument("--cost", type=float, default=0.30)
    ap.add_argument("--out", default="results_crt")
    a = ap.parse_args()

    df = load_bars(a.csv, a.tz)
    print(f"Loaded {len(df):,} bars ({bar_minutes(df)}-min), {df.index[0]} -> {df.index[-1]}", file=sys.stderr)
    tr = run(df, a.start, a.end, a.mss_lookback, a.retrace, a.expiry, a.buffer, a.min_rr,
             a.min_risk, not a.no_partial, a.max_hold, a.cost)
    os.makedirs(a.out, exist_ok=True)
    tr.to_csv(os.path.join(a.out, "trades.csv"), index=False)
    txt = report(tr)
    open(os.path.join(a.out, "summary.txt"), "w").write(txt + "\n")
    print(txt)


if __name__ == "__main__":
    main()
