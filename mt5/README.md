# GoldScalper (MT5 Expert Advisor)

Fast trend-following scalper for XAUUSD with auto lot sizing.

- **Entry:** trades only with the M15 trend (EMA 50 vs 200). On M1, buys a close above the last 10 candles' high in an uptrend, sells a close below their low in a downtrend.
- **Exit:** ATR stop (1.5x ATR), take profit at 2R, ATR trailing stop once 1R in profit.
- **Lots:** starts at `BaseLot` (0.01). With `Compound` on, the lot grows in proportion to the balance
  (balance doubles -> 0.02). After each stop loss the lot is multiplied by `LossLotFactor` (0.5),
  never below `MinLossFactor` of normal size; the next winning trade restores full size. Capped by `MaxLot`.
- **Safety:** max spread, trading hours, max trades/day, daily loss limit (3%), cooldown after a loss.

## Install
1. MT5 -> File -> Open Data Folder -> `MQL5/Experts/`, copy `GoldScalper.mq5` there.
2. Open it in MetaEditor (F4) and press Compile (F7).
3. Drag it onto an XAUUSD chart (any timeframe), enable Algo Trading.

## Test before using real money
1. Strategy Tester (Ctrl+R): Expert = GoldScalper, Symbol = XAUUSD, Modelling = **Every tick based on real ticks**,
   last 1–2 years, your broker's real spread/commission.
2. Check profit factor (>1.3), max drawdown, and that results hold on a period you didn't optimize on.
3. Run on a **demo** account for at least a month before going live.

No settings are guaranteed to be profitable. Hours are broker server time.

---

# GoldSilverBullet (MT5 Expert Advisor)

"First FVG after a sweep" on XAUUSD M5, in the three Silver Bullet windows (New York time):
London 03–04, NY AM 10–11, NY PM 14–15.

- **Liquidity:** high/low of the 3 hours before each window (`LookbackHours`).
- **Sweep:** a 5-min candle in the window trades above that high (look for sells) or below that low (look for buys).
- **Entry:** limit order at the middle (CE) of the first opposite fair value gap after the sweep. Cancelled if not filled by the end of the window.
- **Stop:** beyond the sweep wick. **Target:** the opposite liquidity level (e.g. the high for buys), or a fixed RR.
  Setups whose target is under 1R or whose stop is under $1 are skipped.
- **Lots:** same as GoldScalper — 0.01 start, compounding, halved after a stop loss, reset after a win.
- **Time zone:** set `NYOffsetHours` = broker server time minus New York time (7 for most GMT+2/+3 brokers).
  Check: when it is 10:00 in New York, your MT5 Market Watch clock should read 17:00.

Python version for CSV data: `backtest/fvg_sweep_backtest.py`.

---

# GoldStraddle (MT5 Expert Advisor)

Straddle / stop-and-reverse breakout bot for XAUUSD (the style shown in the bot_ek99 reel).

- **Straddle:** when flat, a BUY STOP is placed above price and a SELL STOP below
  (0.5x ATR away by default, or a fixed $ distance with `UseATR = false`). Unfilled orders follow price each candle.
- **Breakout:** when one side fills, the other is deleted. SL 1x ATR, TP 2x ATR (0 = trailing only), ATR trailing stop.
- **Stop and reverse:** an opposite stop order is parked at the position's stop loss, so a stop-out flips the trade.
  Capped at `MaxReversals` (2) flips per cycle; an unfilled reverse order expires after 3 candles.
- **Lots:** 0.01 start, compounding, halved after a stop loss, reset after a win. Never increases after a loss.
- **Limits:** spread filter, server-time trading hours (9–21), max 20 trades/day, 3% daily loss stop, close at end of day.

**Spread is the biggest risk for this style.** Backtest with *Every tick based on real ticks* and your broker's real
spread, and compare it with GoldScalper and GoldSilverBullet over the same period.
