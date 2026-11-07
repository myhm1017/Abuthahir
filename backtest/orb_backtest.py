#!/usr/bin/env python3
"""
Gold opening-range breakout (ORB) backtest — 9:30 ET, 15-minute range, 5-minute breakout.

Rules (one trade per day, first signal only):
  1. Opening range = high/low of the 09:30–09:45 ET candle (the first 15-min candle).
  2. Signal = the first 5-min candle (from 09:45 on) that CLOSES above the range high
     (long) or below the range low (short).
  3. Entry = open of the next 5-min candle.
  4. Stop  = the signal candle's low (long) / high (short).
  5. Exit management, three variants tested side by side:
       fixed_2R        all out at 2R
       partial_runner  half out at 2R, stop to breakeven, runner trails the previous
                       completed 5-min candle's low/high
       trail_after_2R  full size; when 2R is touched the stop jumps to +1R, then trails
                       the previous completed 5-min candle's low/high
     Anything still open is closed at the end-of-day time (default 16:00 ET).

Intrabar order is conservative: if a bar touches both the stop and the target,
the stop is assumed to have been hit first. Feed 1-minute data where possible —
the exits are then simulated minute by minute, which removes most of that ambiguity.

Input: a CSV of OHLC bars (1-min or 5-min). Supported layouts:
  - time,open,high,low,close[,volume]   (time = ISO string or unix seconds)
  - MT4/MT5 export: <DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,... (tab or comma)
  - Dukascopy/HistData style: Gmt time / Local time, Open, High, Low, Close
Pass --tz with the timezone of the timestamps in the file (default UTC).

Usage:
  python3 orb_backtest.py data/xauusd_1m.csv --tz UTC --start 2025-11-07 --end 2026-11-06
"""
import argparse
import os
import sys

import numpy as np
import pandas as pd

ET = "America/New_York"
VARIANTS = ("fixed_2R", "partial_runner", "trail_after_2R")


# ------------------------------------------------------------------ loading

def load_bars(path, tz):
    sep = "\t" if open(path, encoding="utf-8", errors="ignore").readline().count("\t") > 1 else ","
    df = pd.read_csv(path, sep=sep)
    df.columns = [c.strip().strip("<>").lower() for c in df.columns]

    if "date" in df.columns and "time" in df.columns:
        ts = pd.to_datetime(df["date"].astype(str) + " " + df["time"].astype(str))
    else:
        tcol = next((c for c in df.columns if c in ("time", "datetime", "timestamp", "date",
                                                       "gmt time", "local time")), df.columns[0])
        col = df[tcol]
        if pd.api.types.is_numeric_dtype(col):
            unit = "ms" if col.iloc[0] > 1e11 else "s"
            ts = pd.to_datetime(col, unit=unit, utc=True)
        else:
            ts = pd.to_datetime(col.astype(str), dayfirst="local time" in tcol or "gmt time" in tcol,
                                utc=False, format="mixed")
    ts = pd.DatetimeIndex(ts)
    ts = ts.tz_localize(tz) if ts.tz is None else ts
    out = pd.DataFrame({k: pd.to_numeric(df[k], errors="coerce") for k in ("open", "high", "low", "close")})
    out.index = ts.tz_convert(ET)
    out = out.dropna().sort_index()
    out = out[~out.index.duplicated()]
    return out


def bar_minutes(df):
    return int(pd.Series(df.index[:2000]).diff().dt.total_seconds().div(60).mode().iloc[0])


def to_5m(df):
    return df.resample("5min", label="left", closed="left").agg(
        {"open": "first", "high": "max", "low": "min", "close": "last"}).dropna()


# ------------------------------------------------------------------ simulation

def manage(variant, side, entry, stop, risk, base, m5, entry_time, eod_time, cost):
    """Walk the base bars after entry. Returns (exit_time, R, exit_reason)."""
    sgn = 1 if side == "long" else -1
    target = entry + sgn * 2 * risk
    sl = stop
    size_open = 1.0
    realized = 0.0          # in price units, weighted by size
    hit_2r = False
    last_trail_bar = None

    bars = base[(base.index >= entry_time) & (base.index < eod_time)]
    for t, b in bars.iterrows():
        # trailing: tighten to the previous completed 5-min candle once the runner is active
        if hit_2r and variant != "fixed_2R":
            done = m5[m5.index + pd.Timedelta(minutes=5) <= t]
            if len(done) and done.index[-1] != last_trail_bar:
                last_trail_bar = done.index[-1]
                lvl = done.iloc[-1]["low"] if side == "long" else done.iloc[-1]["high"]
                sl = max(sl, lvl) if side == "long" else min(sl, lvl)

        lo, hi = b["low"], b["high"]
        stop_hit = lo <= sl if side == "long" else hi >= sl
        tgt_hit = (hi >= target if side == "long" else lo <= target) and not hit_2r

        if stop_hit:   # conservative: stop first
            px = min(sl, b["open"]) if side == "long" else max(sl, b["open"])  # gap through stop
            realized += size_open * sgn * (px - entry)
            reason = "stop" if not hit_2r else "trail"
            return t, (realized - cost) / risk, reason
        if tgt_hit:
            hit_2r = True
            if variant == "fixed_2R":
                return t, (2 * risk - cost) / risk, "target"
            if variant == "partial_runner":
                realized += 0.5 * 2 * risk
                size_open = 0.5
                sl = entry
            elif variant == "trail_after_2R":
                sl = entry + sgn * risk

    last = bars.iloc[-1] if len(bars) else base[base.index < eod_time].iloc[-1]
    realized += size_open * sgn * (last["close"] - entry)
    return (bars.index[-1] if len(bars) else entry_time), (realized - cost) / risk, "eod"


def run(df, start, end, eod="16:00", last_entry=None, cost=0.30):
    base = df
    m5 = to_5m(df) if bar_minutes(df) < 5 else df
    trades, days = [], []

    for day, g5 in m5.groupby(m5.index.date):
        if (start and str(day) < start) or (end and str(day) > end):
            continue
        d0 = pd.Timestamp(day).tz_localize(ET)
        orng = g5[(g5.index >= d0 + pd.Timedelta("9h30min")) & (g5.index < d0 + pd.Timedelta("9h45min"))]
        if len(orng) < 3:
            continue  # holiday / no session
        days.append(day)
        orh, orl = orng["high"].max(), orng["low"].min()
        eod_t = d0 + pd.Timedelta(eod.replace(":", "h") + "min")
        cutoff = d0 + pd.Timedelta(last_entry.replace(":", "h") + "min") if last_entry else eod_t
        after = g5[(g5.index >= d0 + pd.Timedelta("9h45min")) & (g5.index < eod_t)]

        for i in range(len(after) - 1):
            sig = after.iloc[i]
            if after.index[i] + pd.Timedelta(minutes=5) > cutoff:
                break
            side = "long" if sig["close"] > orh else "short" if sig["close"] < orl else None
            if side is None:
                continue
            nxt_t = after.index[i + 1]
            entry = after.iloc[i + 1]["open"]
            stop = sig["low"] if side == "long" else sig["high"]
            risk = entry - stop if side == "long" else stop - entry
            rec = dict(date=day, side=side, or_high=orh, or_low=orl, signal_time=after.index[i].strftime("%H:%M"),
                       entry_time=nxt_t.strftime("%H:%M"), entry=entry, stop=stop, risk=round(risk, 2))
            if risk <= 0:
                rec.update({v: np.nan for v in VARIANTS}, note="entry beyond stop - skipped")
                trades.append(rec)
                break
            for v in VARIANTS:
                xt, r, why = manage(v, side, entry, stop, risk, base, m5, nxt_t, eod_t, cost)
                rec[v] = round(r, 3)
                rec[v + "_exit"] = f"{why}@{xt.strftime('%H:%M')}"
            trades.append(rec)
            break
    return pd.DataFrame(trades), days


# ------------------------------------------------------------------ reporting

def stats(r):
    r = r.dropna()
    if r.empty:
        return {}
    eq = r.cumsum()
    dd = (eq - eq.cummax()).min()
    wins, losses = r[r > 0], r[r <= 0]
    streak = cur = 0
    for x in r:
        cur = cur + 1 if x <= 0 else 0
        streak = max(streak, cur)
    return {"trades": len(r), "win_rate_%": round(100 * len(wins) / len(r), 1),
            "total_R": round(r.sum(), 2), "avg_R": round(r.mean(), 3),
            "profit_factor": round(wins.sum() / -losses.sum(), 2) if losses.sum() < 0 else float("inf"),
            "max_drawdown_R": round(dd, 2), "max_losing_streak": streak,
            "best_R": round(r.max(), 2), "worst_R": round(r.min(), 2)}


def report(tr, days):
    lines = [f"Sessions with a 09:30 opening range: {len(days)}",
             f"Days with a trade: {tr[VARIANTS[0]].notna().sum()}  |  no breakout / skipped: "
             f"{len(days) - tr[VARIANTS[0]].notna().sum()}", ""]
    summ = pd.DataFrame({v: stats(tr[v]) for v in VARIANTS})
    lines += ["== Overall ==", summ.to_string(), ""]
    for side in ("long", "short"):
        s = tr[tr.side == side]
        lines += [f"== {side} only ==", pd.DataFrame({v: stats(s[v]) for v in VARIANTS}).to_string(), ""]
    m = tr.dropna(subset=[VARIANTS[0]]).copy()
    m["month"] = pd.to_datetime(m["date"]).dt.strftime("%Y-%m")
    lines += ["== R by month ==", m.groupby("month")[list(VARIANTS)].sum().round(2).to_string()]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv")
    ap.add_argument("--tz", default="UTC", help="timezone of the timestamps in the CSV (e.g. UTC, Etc/GMT-2, America/New_York)")
    ap.add_argument("--start"), ap.add_argument("--end")
    ap.add_argument("--eod", default="16:00", help="flat-by time, ET")
    ap.add_argument("--last-entry", default=None, help="no new signals after this ET time, e.g. 12:00")
    ap.add_argument("--cost", type=float, default=0.30, help="round-trip spread+commission in $ per oz")
    ap.add_argument("--out", default="results")
    a = ap.parse_args()

    df = load_bars(a.csv, a.tz)
    print(f"Loaded {len(df):,} bars ({bar_minutes(df)}-min), {df.index[0]} -> {df.index[-1]}", file=sys.stderr)
    tr, days = run(df, a.start, a.end, a.eod, a.last_entry, a.cost)
    os.makedirs(a.out, exist_ok=True)
    tr.to_csv(os.path.join(a.out, "trades.csv"), index=False)
    txt = report(tr, days)
    open(os.path.join(a.out, "summary.txt"), "w").write(txt + "\n")
    print(txt)


if __name__ == "__main__":
    main()
