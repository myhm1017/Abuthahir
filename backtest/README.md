# Gold 9:30 ORB backtest

Tests this setup on XAUUSD, once per day:

1. Mark the high/low of the first 15-min candle after the 09:30 ET open (09:30–09:45).
2. Wait for a 5-min candle to **close** above the high (long) or below the low (short).
3. Enter at the open of the next candle; stop at the signal candle's low (long) / high (short).
4. Target 2R minimum. Three exit styles are compared:
   - `fixed_2R` – all out at 2R
   - `partial_runner` – half out at 2R, stop to breakeven, runner trails the previous 5-min candle
   - `trail_after_2R` – full size; at 2R the stop moves to +1R, then trails the previous 5-min candle
5. Flat by 16:00 ET. Default cost: $0.30/oz round trip.

## Data

You need 1-minute (best) or 5-minute XAUUSD bars for the period. Any of these work:

- **MetaTrader 5**: Tools → History Center (or View → Symbols → Bars), pick XAUUSD M1, export to CSV.
  Broker server time is usually UTC+2/+3 — pass `--tz Etc/GMT-2` (or `Etc/GMT-3` in summer), or check your broker.
- **Dukascopy** (free): https://www.dukascopy.com/swiss/english/marketwatch/historical/ → XAUUSD, 1 min, export CSV in GMT (`--tz UTC`).
- **TradingView** (paid plans): export chart data from a 1m/5m XAUUSD chart (unix-time column, `--tz UTC`).

## Run

```bash
pip install pandas numpy
python3 orb_backtest.py xauusd_1m.csv --tz UTC --start 2025-11-07 --end 2026-11-06
```

Options: `--last-entry 12:00` (ignore late breakouts), `--eod 16:00`, `--cost 0.30`, `--out results`.
Output: `results/trades.csv` (every day's trade) and `results/summary.txt` (win rate, total R, profit factor,
drawdown, long vs short, R by month).
