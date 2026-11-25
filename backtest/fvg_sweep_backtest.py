#!/usr/bin/env python3
"""
Gold "first FVG after a sweep" backtest (Silver-Bullet style windows), 5-minute chart.

Rules, per window (New York time), max one trade per window:
  Windows      London 03:00-04:00, NY AM 10:00-11:00, NY PM 14:00-15:00
  Liquidity    high/low of the --lookback hours before the window opens
  Sweep        a 5-min candle inside the window trades above the liquidity high
               (-> look for shorts) or below the liquidity low (-> look for longs)
  FVG          the first fair value gap in the opposite direction formed after the sweep:
                 bearish: low[c1] > high[c3]  -> zone high[c3] .. low[c1]
                 bullish: high[c1] < low[c3]  -> zone high[c1] .. low[c3]
  Entry        limit order at the middle of the gap ("CE", consequent encroachment),
               valid until the window closes, then cancelled
  Stop         beyond the sweep extreme (highest high / lowest low since the sweep) + buffer
  Target       --target liquidity (default): the opposite liquidity level, i.e. the range
               high for longs (buy-side liquidity) / range low for shorts; setups offering
               less than --min-rr are skipped. --target rr: fixed --rr x risk.
               Anything open is closed at --eod (16:00 ET).

Fills and exits are simulated on the base bars (1-min data recommended). Conservative:
on the fill bar only the stop counts; if a bar touches stop and target, the stop wins.

Usage:
  python3 fvg_sweep_backtest.py xauusd_1m.csv --tz UTC --start 2025-11-25 --end 2026-11-24
"""
import argparse
import os
import sys

import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from orb_backtest import ET, bar_minutes, load_bars, stats, to_5m  # noqa: E402

WINDOWS = {"London": ("03:00", "04:00"), "NY_AM": ("10:00", "11:00"), "NY_PM": ("14:00", "15:00")}


def hm(day0, s):
    h, m = s.split(":")
    return day0 + pd.Timedelta(hours=int(h), minutes=int(m))


def find_setup(m5, liq_hi, liq_lo, w_start, w_end):
    """Scan the window's 5-min bars. Returns (side, entry, stop, fvg_time) or None."""
    bars = m5[(m5.index >= w_start) & (m5.index < w_end)]
    hist = m5[m5.index < w_end]
    swept = None          # "high" or "low"
    extreme = None
    sweep_t = None
    for t, b in bars.iterrows():
        if swept is None:
            if b["high"] > liq_hi:
                swept, extreme, sweep_t = "high", b["high"], t
            elif b["low"] < liq_lo:
                swept, extreme, sweep_t = "low", b["low"], t
            continue
        extreme = max(extreme, b["high"]) if swept == "high" else min(extreme, b["low"])
        # FVG formed by the last three candles ending at t (c1, c2, c3=t), all after the sweep start
        win = hist[hist.index <= t].iloc[-3:]
        if len(win) < 3 or win.index[0] < sweep_t:
            continue
        c1, c3 = win.iloc[0], win.iloc[2]
        if swept == "high" and c1["low"] > c3["high"]:
            return "short", (c1["low"] + c3["high"]) / 2, extreme, t
        if swept == "low" and c1["high"] < c3["low"]:
            return "long", (c1["high"] + c3["low"]) / 2, extreme, t
    return None


def simulate(base, side, entry, stop, target, start, fill_until, eod):
    """Returns (filled, R_before_cost, reason, exit_time)."""
    bars = base[(base.index >= start) & (base.index < eod)]
    filled = False
    for t, b in bars.iterrows():
        if not filled:
            if t >= fill_until:
                return False, 0.0, "not filled", t
            touched = b["high"] >= entry if side == "short" else b["low"] <= entry
            if not touched:
                continue
            filled = True
            if (b["high"] >= stop) if side == "short" else (b["low"] <= stop):
                return True, -1.0, "stop", t
            continue
        if (b["high"] >= stop) if side == "short" else (b["low"] <= stop):
            return True, -1.0, "stop", t
        if (b["low"] <= target) if side == "short" else (b["high"] >= target):
            return True, None, "target", t
    if not filled:
        return False, 0.0, "not filled", eod
    last = bars.iloc[-1]["close"]
    risk = abs(stop - entry)
    r = (entry - last) / risk if side == "short" else (last - entry) / risk
    return True, r, "eod", bars.index[-1]


def run(df, start, end, lookback=3.0, rr=2.0, buffer=0.10, min_risk=1.0, cost=0.30,
        eod="16:00", windows=tuple(WINDOWS), target_mode="liquidity", min_rr=1.0):
    base = df
    m5 = to_5m(df) if bar_minutes(df) < 5 else df
    rows = []
    for day in sorted(set(m5.index.date)):
        if (start and str(day) < start) or (end and str(day) > end):
            continue
        if pd.Timestamp(day).dayofweek >= 5:
            continue
        d0 = pd.Timestamp(day).tz_localize(ET)
        eod_t = hm(d0, eod)
        for name in windows:
            ws, we = hm(d0, WINDOWS[name][0]), hm(d0, WINDOWS[name][1])
            pre = m5[(m5.index >= ws - pd.Timedelta(hours=lookback)) & (m5.index < ws)]
            inside = m5[(m5.index >= ws) & (m5.index < we)]
            if len(pre) < lookback * 12 * 0.8 or len(inside) < 10:
                continue  # market closed / missing data
            rec = dict(date=day, window=name, liq_high=pre["high"].max(), liq_low=pre["low"].min())
            setup = find_setup(m5, rec["liq_high"], rec["liq_low"], ws, we)
            if setup is None:
                rec["result"] = "no setup"
                rows.append(rec)
                continue
            side, entry, sweep_ext, fvg_t = setup
            stop = sweep_ext + buffer if side == "short" else sweep_ext - buffer
            risk = abs(stop - entry)
            rec.update(side=side, fvg_time=fvg_t.strftime("%H:%M"), entry=round(entry, 2),
                       stop=round(stop, 2), risk=round(risk, 2))
            if risk < min_risk:
                rec["result"] = "stop too tight"
                rows.append(rec)
                continue
            if target_mode == "liquidity":
                target = rec["liq_low"] if side == "short" else rec["liq_high"]
            else:
                target = entry - rr * risk if side == "short" else entry + rr * risk
            reward = (entry - target) if side == "short" else (target - entry)
            rec.update(target=round(target, 2), planned_R=round(reward / risk, 2))
            if reward < min_rr * risk:
                rec["result"] = "target too close"
                rows.append(rec)
                continue
            filled, r, why, xt = simulate(base, side, entry, stop, target,
                                          fvg_t + pd.Timedelta(minutes=5), we, eod_t)
            if not filled:
                rec["result"] = "not filled"
            else:
                r = reward / risk if why == "target" else r
                rec.update(result=why, exit_time=xt.strftime("%H:%M"), R=round(r - cost / risk, 3))
            rows.append(rec)
    return pd.DataFrame(rows)


def report(tr):
    out = []
    counts = tr["result"].value_counts().to_dict()
    out.append("Window outcomes: " + ", ".join(f"{k}: {v}" for k, v in counts.items()))
    t = tr.dropna(subset=["R"]) if "R" in tr else tr.iloc[0:0]
    out += ["", "== All trades ==", pd.Series(stats(t["R"]) if len(t) else {}).to_string()]
    for w in WINDOWS:
        s = t[t.window == w]
        if len(s):
            out += ["", f"== {w} ==", pd.Series(stats(s["R"])).to_string()]
    for side in ("long", "short"):
        s = t[t.side == side]
        if len(s):
            out += ["", f"== {side} ==", pd.Series(stats(s["R"])).to_string()]
    if len(t):
        m = t.copy()
        m["month"] = pd.to_datetime(m["date"]).dt.strftime("%Y-%m")
        out += ["", "== R by month ==", m.groupby("month")["R"].agg(["count", "sum"]).round(2).to_string()]
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv")
    ap.add_argument("--tz", default="UTC")
    ap.add_argument("--start"), ap.add_argument("--end")
    ap.add_argument("--lookback", type=float, default=3.0, help="hours before the window that define liquidity")
    ap.add_argument("--target", choices=["liquidity", "rr"], default="liquidity")
    ap.add_argument("--rr", type=float, default=2.0, help="fixed R target when --target rr")
    ap.add_argument("--min-rr", type=float, default=1.0, help="skip liquidity targets under this R")
    ap.add_argument("--buffer", type=float, default=0.10, help="$ beyond the sweep extreme for the stop")
    ap.add_argument("--min-risk", type=float, default=1.0, help="skip setups with a stop under this many $")
    ap.add_argument("--cost", type=float, default=0.30, help="round-trip spread+commission, $ per oz")
    ap.add_argument("--eod", default="16:00")
    ap.add_argument("--out", default="results_fvg")
    a = ap.parse_args()

    df = load_bars(a.csv, a.tz)
    print(f"Loaded {len(df):,} bars ({bar_minutes(df)}-min), {df.index[0]} -> {df.index[-1]}", file=sys.stderr)
    tr = run(df, a.start, a.end, a.lookback, a.rr, a.buffer, a.min_risk, a.cost, a.eod,
             target_mode=a.target, min_rr=a.min_rr)
    os.makedirs(a.out, exist_ok=True)
    tr.to_csv(os.path.join(a.out, "trades.csv"), index=False)
    txt = report(tr)
    open(os.path.join(a.out, "summary.txt"), "w").write(txt + "\n")
    print(txt)


if __name__ == "__main__":
    main()
