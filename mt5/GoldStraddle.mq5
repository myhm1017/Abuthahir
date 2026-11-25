//+------------------------------------------------------------------+
//| GoldStraddle.mq5                                                  |
//| Straddle / stop-and-reverse breakout bot for XAUUSD (MT5)         |
//|                                                                   |
//| 1. When flat, place a BUY STOP above and a SELL STOP below price  |
//|    (fixed $ distance or a fraction of ATR). Re-centred on every   |
//|    new candle until one fills.                                    |
//| 2. When one side fills, the other is deleted. If StopAndReverse   |
//|    is on, an opposite stop order is parked at the position's      |
//|    stop loss, so a stop-out flips the trade the other way         |
//|    (capped by MaxReversals per cycle to limit chop damage).       |
//| 3. Tight SL, optional TP, trailing stop; the reverse order        |
//|    follows the trailed stop.                                      |
//| Lots: 0.01 start, compounding with balance, cut after a SL.       |
//| Lots NEVER increase after a loss (no martingale).                 |
//|                                                                   |
//| NOT a guarantee of profit. Backtest with "Every tick based on     |
//| real ticks" (spread matters a lot here) and run on demo first.    |
//+------------------------------------------------------------------+
#property copyright "Abuthahir"
#property version   "1.00"

#include <Trade/Trade.mqh>

input group "Lot size & compounding"
input double BaseLot         = 0.01;  // Starting lot size
input bool   Compound        = true;  // Grow lot with the balance
input double StartBalance    = 0;     // Balance that BaseLot belongs to (0 = balance at start)
input double LossLotFactor   = 0.5;   // Multiply lot after a stop loss (1 = off)
input double MinLossFactor   = 0.25;  // Never shrink below this fraction of normal lot
input double MaxLot          = 5.0;   // Hard cap

input group "Orders"
input ENUM_TIMEFRAMES TF     = PERIOD_M5; // Candle timeframe (re-centring, ATR)
input bool   UseATR          = true;  // Distances from ATR (true) or fixed $ (false)
input int    ATRPeriod       = 14;
input double EntryATR        = 0.5;   // Pending distance from price = ATR x this
input double SLATR           = 1.0;   // Stop loss = ATR x this
input double TPATR           = 2.0;   // Take profit = ATR x this (0 = none, trailing only)
input double EntryUSD        = 1.5;   // Fixed: pending distance ($)
input double SLUSD           = 3.0;   // Fixed: stop loss ($)
input double TPUSD           = 6.0;   // Fixed: take profit ($, 0 = none)
input bool   RecenterEachBar = true;  // Move unfilled straddle with price every candle

input group "Stop and reverse"
input bool   StopAndReverse  = true;  // Park an opposite stop order at the stop loss
input int    MaxReversals    = 2;     // Max flips per cycle (0 = never flip)
input int    ReverseExpiryBars = 3;   // Cancel an unfilled reverse order after N candles

input group "Trailing"
input bool   UseTrailing     = true;
input double TrailStartATR   = 1.0;   // Start trailing after ATR x this in profit (fixed: x SLUSD/SLATR ratio)
input double TrailATR        = 0.8;   // Trail distance = ATR x this

input group "Filters & risk limits"
input int    MaxSpreadPoints = 35;    // No new straddles above this spread (points)
input int    StartHour       = 9;     // Server hour trading starts
input int    EndHour         = 21;    // Server hour: cancel orders, stop new trades
input bool   CloseAtEnd      = true;  // Also close open trades at EndHour
input int    MaxTradesPerDay = 20;
input double MaxDailyLossPct = 3.0;   // Stop for the day after losing this % (0 = off)
input long   Magic           = 20261126;

CTrade trade;
int    hATR = INVALID_HANDLE;
double g_startBalance = 0, g_lossMult = 1.0;
string gvStart, gvMult;
ulong  g_lastPos   = 0;     // position ticket seen last tick
int    g_fills     = 0;     // fills in the current cycle (1 = first breakout)
datetime g_revPlaced = 0;   // when the reverse order was parked

//+------------------------------------------------------------------+
int OnInit()
{
   hATR = iATR(_Symbol, TF, ATRPeriod);
   if(hATR == INVALID_HANDLE) return INIT_FAILED;
   trade.SetExpertMagicNumber(Magic);
   trade.SetTypeFillingBySymbol(_Symbol);
   trade.SetDeviationInPoints(30);
   gvStart = StringFormat("ST_%s_%I64d_start", _Symbol, Magic);
   gvMult  = StringFormat("ST_%s_%I64d_mult",  _Symbol, Magic);
   if(StartBalance > 0)                  g_startBalance = StartBalance;
   else if(GlobalVariableCheck(gvStart)) g_startBalance = GlobalVariableGet(gvStart);
   else                                  g_startBalance = AccountInfoDouble(ACCOUNT_BALANCE);
   GlobalVariableSet(gvStart, g_startBalance);
   if(GlobalVariableCheck(gvMult)) g_lossMult = GlobalVariableGet(gvMult);
   return INIT_SUCCEEDED;
}

void OnDeinit(const int reason) { IndicatorRelease(hATR); }

//+------------------------------------------------------------------+
double NormalizeLot(double lot)
{
   double step = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);
   double mn   = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);
   double mx   = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MAX);
   lot = MathFloor(lot / step + 1e-9) * step;
   lot = MathMax(mn, MathMin(MathMin(mx, MaxLot), lot));
   int d = MathMax(0, MathMin(8, (int)MathCeil(-MathLog10(step))));
   return NormalizeDouble(lot, d);
}

double CalcLot()
{
   double lot = BaseLot;
   if(Compound && g_startBalance > 0)
      lot = BaseLot * AccountInfoDouble(ACCOUNT_BALANCE) / g_startBalance;
   return NormalizeLot(lot * g_lossMult);
}

double ATR()
{
   double b[];
   if(CopyBuffer(hATR, 0, 1, 1, b) != 1) return 0;
   return b[0];
}

// distances in price
bool Distances(double &entry, double &sl, double &tp, double &trailStart, double &trailDist)
{
   double atr = ATR();
   if(UseATR)
   {
      if(atr <= 0) return false;
      entry = EntryATR * atr; sl = SLATR * atr; tp = TPATR * atr;
      trailStart = TrailStartATR * atr; trailDist = TrailATR * atr;
   }
   else
   {
      entry = EntryUSD; sl = SLUSD; tp = TPUSD;
      double ratio = SLATR > 0 ? SLUSD / SLATR : 1;   // keep the ATR inputs' proportions
      trailStart = TrailStartATR * ratio; trailDist = TrailATR * ratio;
   }
   double minD = (SymbolInfoInteger(_Symbol, SYMBOL_TRADE_STOPS_LEVEL) + 2) * _Point;
   entry = MathMax(entry, minD); sl = MathMax(sl, minD);
   if(tp > 0) tp = MathMax(tp, minD);
   return true;
}

bool GetPosition(ulong &ticket, long &type, double &open, double &sl, double &tp)
{
   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong t = PositionGetTicket(i);
      if(t && PositionGetString(POSITION_SYMBOL) == _Symbol && PositionGetInteger(POSITION_MAGIC) == Magic)
      {
         ticket = t; type = PositionGetInteger(POSITION_TYPE);
         open = PositionGetDouble(POSITION_PRICE_OPEN);
         sl = PositionGetDouble(POSITION_SL); tp = PositionGetDouble(POSITION_TP);
         return true;
      }
   }
   return false;
}

int PendingCount(ulong &first)
{
   int n = 0; first = 0;
   for(int i = OrdersTotal() - 1; i >= 0; i--)
   {
      ulong t = OrderGetTicket(i);
      if(t && OrderGetString(ORDER_SYMBOL) == _Symbol && OrderGetInteger(ORDER_MAGIC) == Magic)
      { n++; first = t; }
   }
   return n;
}

void DeletePending()
{
   for(int i = OrdersTotal() - 1; i >= 0; i--)
   {
      ulong t = OrderGetTicket(i);
      if(t && OrderGetString(ORDER_SYMBOL) == _Symbol && OrderGetInteger(ORDER_MAGIC) == Magic)
         trade.OrderDelete(t);
   }
}

void CloseAll()
{
   ulong t; long ty; double o, s, p;
   while(GetPosition(t, ty, o, s, p)) if(!trade.PositionClose(t)) break;
}

void TodayStats(int &entries, double &pnl)
{
   entries = 0; pnl = 0;
   if(!HistorySelect(iTime(_Symbol, PERIOD_D1, 0), TimeCurrent() + 60)) return;
   for(int i = HistoryDealsTotal() - 1; i >= 0; i--)
   {
      ulong d = HistoryDealGetTicket(i);
      if(HistoryDealGetInteger(d, DEAL_MAGIC) != Magic || HistoryDealGetString(d, DEAL_SYMBOL) != _Symbol) continue;
      long e = HistoryDealGetInteger(d, DEAL_ENTRY);
      if(e == DEAL_ENTRY_IN) entries++;
      if(e == DEAL_ENTRY_OUT)
         pnl += HistoryDealGetDouble(d, DEAL_PROFIT) + HistoryDealGetDouble(d, DEAL_SWAP)
              + HistoryDealGetDouble(d, DEAL_COMMISSION);
   }
}

bool CanOpenNew()
{
   MqlDateTime t; TimeToStruct(TimeCurrent(), t);
   if(t.hour < StartHour || t.hour >= EndHour) return false;
   int entries = 0; double pnl = 0;
   TodayStats(entries, pnl);
   if(entries >= MaxTradesPerDay) return false;
   if(MaxDailyLossPct > 0 && pnl < 0)
   {
      double dayStart = AccountInfoDouble(ACCOUNT_BALANCE) - pnl;
      if(-pnl >= dayStart * MaxDailyLossPct / 100.0) return false;
   }
   return true;
}

//+------------------------------------------------------------------+
void PlaceStraddle()
{
   double e, s, p, ts, td;
   if(!Distances(e, s, p, ts, td)) return;
   if(SymbolInfoInteger(_Symbol, SYMBOL_SPREAD) > MaxSpreadPoints) return;
   double ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK), bid = SymbolInfoDouble(_Symbol, SYMBOL_BID);
   double lot = CalcLot();
   double bp = NormalizeDouble(ask + e, _Digits), sp = NormalizeDouble(bid - e, _Digits);
   trade.BuyStop (lot, bp, _Symbol, NormalizeDouble(bp - s, _Digits),
                  p > 0 ? NormalizeDouble(bp + p, _Digits) : 0, ORDER_TIME_GTC, 0, "Straddle");
   trade.SellStop(lot, sp, _Symbol, NormalizeDouble(sp + s, _Digits),
                  p > 0 ? NormalizeDouble(sp - p, _Digits) : 0, ORDER_TIME_GTC, 0, "Straddle");
   g_fills = 0;
}

// opposite stop order parked at the position's stop loss
void SyncReverse(long posType, double posSL)
{
   if(!StopAndReverse || g_fills > MaxReversals || posSL <= 0) { DeletePending(); return; }
   double e, s, p, ts, td;
   if(!Distances(e, s, p, ts, td)) return;
   double price = NormalizeDouble(posSL, _Digits);
   double sl = posType == POSITION_TYPE_BUY ? price + s : price - s;
   double tp = p > 0 ? (posType == POSITION_TYPE_BUY ? price - p : price + p) : 0;
   sl = NormalizeDouble(sl, _Digits); tp = NormalizeDouble(tp, _Digits);

   ulong ord; int n = PendingCount(ord);
   if(n > 1) { DeletePending(); n = 0; }
   if(n == 1 && OrderSelect(ord))
   {
      if(MathAbs(OrderGetDouble(ORDER_PRICE_OPEN) - price) > _Point)
         trade.OrderModify(ord, price, sl, tp, ORDER_TIME_GTC, 0);
      return;
   }
   double lot = CalcLot();
   bool ok = posType == POSITION_TYPE_BUY
      ? trade.SellStop(lot, price, _Symbol, sl, tp, ORDER_TIME_GTC, 0, "Reverse")
      : trade.BuyStop (lot, price, _Symbol, sl, tp, ORDER_TIME_GTC, 0, "Reverse");
   if(ok) g_revPlaced = TimeCurrent();
}

void Trail(ulong ticket, long type, double open, double sl, double tp)
{
   if(!UseTrailing) return;
   double e, s, p, ts, td;
   if(!Distances(e, s, p, ts, td)) return;
   double minD = (SymbolInfoInteger(_Symbol, SYMBOL_TRADE_STOPS_LEVEL) + 2) * _Point;
   td = MathMax(td, minD);
   if(type == POSITION_TYPE_BUY)
   {
      double bid = SymbolInfoDouble(_Symbol, SYMBOL_BID);
      if(bid - open < ts) return;
      double nsl = NormalizeDouble(bid - td, _Digits);
      if(nsl > sl + _Point) trade.PositionModify(ticket, nsl, tp);
   }
   else
   {
      double ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK);
      if(open - ask < ts) return;
      double nsl = NormalizeDouble(ask + td, _Digits);
      if(sl == 0 || nsl < sl - _Point) trade.PositionModify(ticket, nsl, tp);
   }
}

//+------------------------------------------------------------------+
void OnTick()
{
   static datetime lastBar = 0;
   datetime bar = iTime(_Symbol, TF, 0);
   bool newBar = bar != lastBar;
   lastBar = bar;

   ulong pos; long type; double open, sl, tp;
   MqlDateTime t; TimeToStruct(TimeCurrent(), t);
   if(t.hour >= EndHour)
   {
      DeletePending();
      if(CloseAtEnd) CloseAll();
      else if(GetPosition(pos, type, open, sl, tp)) Trail(pos, type, open, sl, tp);
      g_lastPos = 0;
      return;
   }

   if(GetPosition(pos, type, open, sl, tp))
   {
      if(pos != g_lastPos)          // a new fill: breakout or reversal
      {
         g_lastPos = pos;
         g_fills++;
         DeletePending();           // remove the other side of the straddle / old reverse
      }
      Trail(pos, type, open, sl, tp);
      if(PositionSelectByTicket(pos)) sl = PositionGetDouble(POSITION_SL);
      SyncReverse(type, sl);
      return;
   }

   // flat
   g_lastPos = 0;
   ulong ord; int n = PendingCount(ord);
   if(n == 1 && g_fills > 0)        // leftover reverse order that did not fill
   {
      if(TimeCurrent() - g_revPlaced >= ReverseExpiryBars * PeriodSeconds(TF)) DeletePending();
      return;
   }
   if(n >= 2 && newBar && RecenterEachBar) { DeletePending(); n = 0; }
   if(n == 1 && g_fills == 0) { DeletePending(); n = 0; }   // half a straddle: rebuild
   if(n == 0 && CanOpenNew()) PlaceStraddle();
}

//+------------------------------------------------------------------+
void OnTradeTransaction(const MqlTradeTransaction &trans, const MqlTradeRequest &req, const MqlTradeResult &res)
{
   if(trans.type != TRADE_TRANSACTION_DEAL_ADD || !HistoryDealSelect(trans.deal)) return;
   if(HistoryDealGetInteger(trans.deal, DEAL_MAGIC) != Magic) return;
   if(HistoryDealGetString(trans.deal, DEAL_SYMBOL) != _Symbol) return;
   if(HistoryDealGetInteger(trans.deal, DEAL_ENTRY) != DEAL_ENTRY_OUT) return;
   double pnl = HistoryDealGetDouble(trans.deal, DEAL_PROFIT) + HistoryDealGetDouble(trans.deal, DEAL_SWAP)
              + HistoryDealGetDouble(trans.deal, DEAL_COMMISSION);
   if(pnl < 0 && HistoryDealGetInteger(trans.deal, DEAL_REASON) == DEAL_REASON_SL)
      g_lossMult = MathMax(MinLossFactor, g_lossMult * LossLotFactor);
   else if(pnl > 0)
      g_lossMult = 1.0;
   GlobalVariableSet(gvMult, g_lossMult);
}
//+------------------------------------------------------------------+
