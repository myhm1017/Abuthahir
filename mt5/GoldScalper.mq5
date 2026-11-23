//+------------------------------------------------------------------+
//| GoldScalper.mq5                                                   |
//| Fast trend-following scalper for XAUUSD (MetaTrader 5)            |
//|                                                                   |
//| Entry : trade only with the higher-timeframe trend (EMA fast/slow |
//|         on TrendTF). On the signal timeframe (M1 by default) buy  |
//|         when a candle closes above the highest high of the last N |
//|         candles in an uptrend; sell on a close below the lowest   |
//|         low in a downtrend. One position at a time.               |
//| Exit  : ATR-based stop, take profit at RR x stop, optional ATR    |
//|         trailing stop once the trade is in profit.                |
//| Lots  : starts at BaseLot (0.01). Grows in proportion to the      |
//|         balance (compounding) and is multiplied by LossLotFactor  |
//|         after every stop-loss hit; back to full size after a win. |
//| Safety: spread filter, trading hours, max trades per day, daily   |
//|         loss limit, cooldown after a loss, max lot cap.           |
//|                                                                   |
//| NOT a guarantee of profit. Backtest in the Strategy Tester with   |
//| "Every tick based on real ticks", then run on a demo account.     |
//+------------------------------------------------------------------+
#property copyright "Abuthahir"
#property version   "1.00"

#include <Trade/Trade.mqh>

input group "Lot size & compounding"
input double BaseLot          = 0.01;  // Starting lot size
input bool   Compound         = true;  // Grow lot with the balance
input double StartBalance     = 0;     // Balance that BaseLot belongs to (0 = balance when EA starts)
input double LossLotFactor    = 0.5;   // Multiply lot by this after a stop loss (1 = off)
input double MinLossFactor    = 0.25;  // Never shrink below this fraction of the normal lot
input double MaxLot           = 5.0;   // Hard cap on lot size

input group "Strategy"
input ENUM_TIMEFRAMES SignalTF = PERIOD_M1;   // Entry timeframe
input ENUM_TIMEFRAMES TrendTF  = PERIOD_M15;  // Trend timeframe
input int    TrendFastEMA     = 50;    // Trend fast EMA
input int    TrendSlowEMA     = 200;   // Trend slow EMA
input int    BreakoutBars     = 10;    // Breakout lookback (candles)
input int    ATRPeriod        = 14;    // ATR period (signal timeframe)
input double SL_ATR           = 1.5;   // Stop = ATR x this
input double RR               = 2.0;   // Take profit = stop x this (0 = no TP)
input bool   UseTrailing      = true;  // Trail the stop
input double TrailStartR      = 1.0;   // Start trailing after this many R in profit
input double TrailATR         = 1.0;   // Trail distance = ATR x this

input group "Filters & risk limits"
input int    MaxSpreadPoints  = 35;    // Skip entries if spread is wider (points)
input int    StartHour        = 8;     // Trading starts (server hour)
input int    EndHour          = 20;    // Trading ends (server hour)
input int    MaxTradesPerDay  = 10;    // Max entries per day
input double MaxDailyLossPct  = 3.0;   // Stop for the day after losing this % (0 = off)
input int    CooldownBars     = 5;     // Wait this many signal candles after a loss
input long   Magic            = 20261123;
input int    SlippagePoints   = 20;

CTrade trade;
int    hFast = INVALID_HANDLE, hSlow = INVALID_HANDLE, hATR = INVALID_HANDLE;
double g_startBalance = 0;
double g_lossMult     = 1.0;
datetime g_lastLoss   = 0;
string gvStart, gvMult;

//+------------------------------------------------------------------+
int OnInit()
{
   hFast = iMA(_Symbol, TrendTF, TrendFastEMA, 0, MODE_EMA, PRICE_CLOSE);
   hSlow = iMA(_Symbol, TrendTF, TrendSlowEMA, 0, MODE_EMA, PRICE_CLOSE);
   hATR  = iATR(_Symbol, SignalTF, ATRPeriod);
   if(hFast == INVALID_HANDLE || hSlow == INVALID_HANDLE || hATR == INVALID_HANDLE)
   {
      Print("Failed to create indicators");
      return INIT_FAILED;
   }

   trade.SetExpertMagicNumber(Magic);
   trade.SetDeviationInPoints(SlippagePoints);
   trade.SetTypeFillingBySymbol(_Symbol);

   // persist compounding base and loss multiplier across restarts
   gvStart = StringFormat("GS_%s_%I64d_start", _Symbol, Magic);
   gvMult  = StringFormat("GS_%s_%I64d_mult",  _Symbol, Magic);
   if(StartBalance > 0)                 g_startBalance = StartBalance;
   else if(GlobalVariableCheck(gvStart)) g_startBalance = GlobalVariableGet(gvStart);
   else                                  g_startBalance = AccountInfoDouble(ACCOUNT_BALANCE);
   GlobalVariableSet(gvStart, g_startBalance);
   if(GlobalVariableCheck(gvMult)) g_lossMult = GlobalVariableGet(gvMult);

   PrintFormat("GoldScalper started. Base %.2f lot per %.2f balance, loss multiplier %.2f",
               BaseLot, g_startBalance, g_lossMult);
   return INIT_SUCCEEDED;
}

void OnDeinit(const int reason)
{
   IndicatorRelease(hFast);
   IndicatorRelease(hSlow);
   IndicatorRelease(hATR);
}

//+------------------------------------------------------------------+
double Buf(int handle, int shift)
{
   double b[];
   if(CopyBuffer(handle, 0, shift, 1, b) != 1) return EMPTY_VALUE;
   return b[0];
}

int LotDigits()
{
   double step = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);
   int d = (int)MathCeil(-MathLog10(step));
   return MathMax(0, MathMin(8, d));
}

double NormalizeLot(double lot)
{
   double step = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);
   double mn   = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);
   double mx   = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MAX);
   lot = MathFloor(lot / step + 1e-9) * step;
   lot = MathMax(mn, MathMin(MathMin(mx, MaxLot), lot));
   return NormalizeDouble(lot, LotDigits());
}

double CalcLot()
{
   double lot = BaseLot;
   if(Compound && g_startBalance > 0)
      lot = BaseLot * AccountInfoDouble(ACCOUNT_BALANCE) / g_startBalance;
   lot *= g_lossMult;
   return NormalizeLot(lot);
}

bool HasPosition(ulong &ticket)
{
   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong t = PositionGetTicket(i);
      if(t == 0) continue;
      if(PositionGetString(POSITION_SYMBOL) == _Symbol && PositionGetInteger(POSITION_MAGIC) == Magic)
      {
         ticket = t;
         return true;
      }
   }
   return false;
}

// today's entries and closed P/L for this EA
void TodayStats(int &entries, double &pnl)
{
   entries = 0; pnl = 0;
   datetime dayStart = iTime(_Symbol, PERIOD_D1, 0);
   if(!HistorySelect(dayStart, TimeCurrent() + 60)) return;
   for(int i = HistoryDealsTotal() - 1; i >= 0; i--)
   {
      ulong d = HistoryDealGetTicket(i);
      if(HistoryDealGetInteger(d, DEAL_MAGIC) != Magic || HistoryDealGetString(d, DEAL_SYMBOL) != _Symbol)
         continue;
      long entry = HistoryDealGetInteger(d, DEAL_ENTRY);
      if(entry == DEAL_ENTRY_IN) entries++;
      if(entry == DEAL_ENTRY_OUT)
         pnl += HistoryDealGetDouble(d, DEAL_PROFIT) + HistoryDealGetDouble(d, DEAL_SWAP)
              + HistoryDealGetDouble(d, DEAL_COMMISSION);
   }
}

//+------------------------------------------------------------------+
void ManageTrailing(ulong ticket)
{
   if(!UseTrailing || !PositionSelectByTicket(ticket)) return;
   double atr = Buf(hATR, 1);
   if(atr == EMPTY_VALUE) return;

   long   type  = PositionGetInteger(POSITION_TYPE);
   double open  = PositionGetDouble(POSITION_PRICE_OPEN);
   double sl    = PositionGetDouble(POSITION_SL);
   double tp    = PositionGetDouble(POSITION_TP);
   double start = TrailStartR * SL_ATR * atr;
   double dist  = TrailATR * atr;
   double minD  = SymbolInfoInteger(_Symbol, SYMBOL_TRADE_STOPS_LEVEL) * _Point;

   if(type == POSITION_TYPE_BUY)
   {
      double bid = SymbolInfoDouble(_Symbol, SYMBOL_BID);
      if(bid - open < start) return;
      double nsl = NormalizeDouble(bid - MathMax(dist, minD), _Digits);
      if(nsl > sl + _Point) trade.PositionModify(ticket, nsl, tp);
   }
   else
   {
      double ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK);
      if(open - ask < start) return;
      double nsl = NormalizeDouble(ask + MathMax(dist, minD), _Digits);
      if(sl == 0 || nsl < sl - _Point) trade.PositionModify(ticket, nsl, tp);
   }
}

//+------------------------------------------------------------------+
void OnTick()
{
   ulong ticket = 0;
   if(HasPosition(ticket))
   {
      ManageTrailing(ticket);
      return;
   }

   // act once per new signal candle
   static datetime lastBar = 0;
   datetime bar = iTime(_Symbol, SignalTF, 0);
   if(bar == lastBar) return;
   lastBar = bar;

   // ---- filters
   MqlDateTime dt;
   TimeToStruct(TimeCurrent(), dt);
   if(dt.hour < StartHour || dt.hour >= EndHour) return;
   if(SymbolInfoInteger(_Symbol, SYMBOL_SPREAD) > MaxSpreadPoints) return;
   if(g_lastLoss > 0 && TimeCurrent() < g_lastLoss + CooldownBars * PeriodSeconds(SignalTF)) return;

   int entries = 0; double pnl = 0;
   TodayStats(entries, pnl);
   if(entries >= MaxTradesPerDay) return;
   if(MaxDailyLossPct > 0)
   {
      double dayStartBal = AccountInfoDouble(ACCOUNT_BALANCE) - pnl;
      if(pnl < 0 && -pnl >= dayStartBal * MaxDailyLossPct / 100.0) return;
   }

   // ---- trend
   double fast = Buf(hFast, 1), slow = Buf(hSlow, 1), atr = Buf(hATR, 1);
   if(fast == EMPTY_VALUE || slow == EMPTY_VALUE || atr == EMPTY_VALUE || atr <= 0) return;
   double trendClose = iClose(_Symbol, TrendTF, 1);
   bool up   = fast > slow && trendClose > fast;
   bool down = fast < slow && trendClose < fast;
   if(!up && !down) return;

   // ---- breakout of the last N candles (excluding the signal candle)
   double highs[], lows[];
   if(CopyHigh(_Symbol, SignalTF, 2, BreakoutBars, highs) != BreakoutBars) return;
   if(CopyLow (_Symbol, SignalTF, 2, BreakoutBars, lows)  != BreakoutBars) return;
   double hh = highs[ArrayMaximum(highs)];
   double ll = lows[ArrayMinimum(lows)];
   double c1 = iClose(_Symbol, SignalTF, 1);

   double minD   = SymbolInfoInteger(_Symbol, SYMBOL_TRADE_STOPS_LEVEL) * _Point;
   double slDist = MathMax(SL_ATR * atr, minD + _Point);
   double lot    = CalcLot();

   if(up && c1 > hh)
   {
      double ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK);
      double sl  = NormalizeDouble(ask - slDist, _Digits);
      double tp  = RR > 0 ? NormalizeDouble(ask + RR * slDist, _Digits) : 0;
      if(trade.Buy(lot, _Symbol, ask, sl, tp, "GoldScalper"))
         PrintFormat("BUY %.2f lot @ %.2f  SL %.2f  TP %.2f", lot, ask, sl, tp);
   }
   else if(down && c1 < ll)
   {
      double bid = SymbolInfoDouble(_Symbol, SYMBOL_BID);
      double sl  = NormalizeDouble(bid + slDist, _Digits);
      double tp  = RR > 0 ? NormalizeDouble(bid - RR * slDist, _Digits) : 0;
      if(trade.Sell(lot, _Symbol, bid, sl, tp, "GoldScalper"))
         PrintFormat("SELL %.2f lot @ %.2f  SL %.2f  TP %.2f", lot, bid, sl, tp);
   }
}

//+------------------------------------------------------------------+
// lot reduction after a stop loss, reset after a win
void OnTradeTransaction(const MqlTradeTransaction &trans, const MqlTradeRequest &req, const MqlTradeResult &res)
{
   if(trans.type != TRADE_TRANSACTION_DEAL_ADD) return;
   if(!HistoryDealSelect(trans.deal)) return;
   if(HistoryDealGetInteger(trans.deal, DEAL_MAGIC) != Magic) return;
   if(HistoryDealGetString(trans.deal, DEAL_SYMBOL) != _Symbol) return;
   if(HistoryDealGetInteger(trans.deal, DEAL_ENTRY) != DEAL_ENTRY_OUT) return;

   double pnl = HistoryDealGetDouble(trans.deal, DEAL_PROFIT) + HistoryDealGetDouble(trans.deal, DEAL_SWAP)
              + HistoryDealGetDouble(trans.deal, DEAL_COMMISSION);
   long reason = HistoryDealGetInteger(trans.deal, DEAL_REASON);

   if(pnl < 0)
   {
      g_lastLoss = TimeCurrent();
      if(reason == DEAL_REASON_SL)
         g_lossMult = MathMax(MinLossFactor, g_lossMult * LossLotFactor);
   }
   else if(pnl > 0)
      g_lossMult = 1.0;

   GlobalVariableSet(gvMult, g_lossMult);
   PrintFormat("Closed %.2f. Next lot multiplier %.2f -> next lot %.2f", pnl, g_lossMult, CalcLot());
}
//+------------------------------------------------------------------+
