//+------------------------------------------------------------------+
//| GoldCRT.mq5                                                       |
//| Candle Range Theory robot for XAUUSD, with ICT structure shift,   |
//| close-based (MSNR) confirmation and kill-zone timing (QT).        |
//|                                                                   |
//| Range   : previous 4-hour block, aligned to New York time         |
//|           (02, 06, 10, 14, 18, 22), built from M5 candles.        |
//| Sweep   : current block trades beyond the range high or low,      |
//|           inside a kill zone (London 02-05, New York 07-11 ET).   |
//| Confirm : a 5-min candle CLOSES back inside the range and beyond  |
//|           the structure before the sweep (lowest low / highest    |
//|           high of the MSSLookback candles before the extreme).    |
//| Entry   : limit at Retrace of the sweep->MSS leg (0 = market).    |
//| Stop    : beyond the sweep wick. Target: other side of the range. |
//|           Optional half off at the range midpoint + breakeven.    |
//| Lots    : 0.01 start, compounding, cut after a stop loss.         |
//|                                                                   |
//| NOT a guarantee of profit. Backtest with "Every tick based on     |
//| real ticks" and run on demo first.                                |
//+------------------------------------------------------------------+
#property copyright "Abuthahir"
#property version   "1.00"

#include <Trade/Trade.mqh>

input group "Lot size & compounding"
input double BaseLot        = 0.01;  // Starting lot size
input bool   Compound       = true;  // Grow lot with the balance
input double StartBalance   = 0;     // Balance that BaseLot belongs to (0 = balance at start)
input double LossLotFactor  = 0.5;   // Multiply lot after a stop loss (1 = off)
input double MinLossFactor  = 0.25;  // Never shrink below this fraction of normal lot
input double MaxLot         = 5.0;   // Hard cap

input group "Time (New York)"
input int    NYOffsetHours  = 7;     // Broker server time minus New York time (most brokers: 7)
input bool   LondonKZ       = true;  // Kill zone 02:00-05:00
input bool   NewYorkKZ      = true;  // Kill zone 07:00-11:00

input group "Setup"
input int    MSSLookback    = 6;     // 5-min candles before the sweep that define structure
input double Retrace        = 0.5;   // Entry retrace of sweep->MSS leg (0 = market)
input int    ExpiryBars     = 6;     // Limit order lifetime (5-min candles)
input double StopBuffer     = 0.10;  // $ beyond the sweep wick
input double MinRR          = 1.5;   // Skip if the range target is closer than this
input double MinStop        = 1.0;   // Skip setups with a stop smaller than this ($)
input bool   PartialAtMid   = true;  // Half off at the range midpoint, rest to breakeven
input double MaxHoldHours   = 8;     // Close trades older than this
input int    MaxSpreadPoints = 40;
input long   Magic          = 20261128;

CTrade trade;
double g_startBalance = 0, g_lossMult = 1.0;
string gvStart, gvMult;

// block state
datetime b_startNY = 0;
double   b_hi, b_lo, b_extreme;
int      b_swept;               // 0 none, 1 high, -1 low
datetime b_extremeTime;
bool     b_done;
// trade state
double   g_mid = 0;
bool     g_partialDone = false;
datetime g_orderExpiry = 0;

//+------------------------------------------------------------------+
int OnInit()
{
   trade.SetExpertMagicNumber(Magic);
   trade.SetTypeFillingBySymbol(_Symbol);
   trade.SetDeviationInPoints(30);
   gvStart = StringFormat("CRT_%s_%I64d_start", _Symbol, Magic);
   gvMult  = StringFormat("CRT_%s_%I64d_mult",  _Symbol, Magic);
   if(StartBalance > 0)                  g_startBalance = StartBalance;
   else if(GlobalVariableCheck(gvStart)) g_startBalance = GlobalVariableGet(gvStart);
   else                                  g_startBalance = AccountInfoDouble(ACCOUNT_BALANCE);
   GlobalVariableSet(gvStart, g_startBalance);
   if(GlobalVariableCheck(gvMult)) g_lossMult = GlobalVariableGet(gvMult);
   return INIT_SUCCEEDED;
}

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

datetime ToNY(datetime s)     { return s - NYOffsetHours * 3600; }
datetime ToServer(datetime n) { return n + NYOffsetHours * 3600; }

bool InKillZone(datetime ny)
{
   MqlDateTime t; TimeToStruct(ny, t);
   int m = t.hour * 60 + t.min;
   return (LondonKZ && m >= 120 && m < 300) || (NewYorkKZ && m >= 420 && m < 660);
}

bool GetPosition(ulong &ticket, long &type, double &open, double &sl, double &tp, datetime &opened, double &vol)
{
   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong t = PositionGetTicket(i);
      if(t && PositionGetString(POSITION_SYMBOL) == _Symbol && PositionGetInteger(POSITION_MAGIC) == Magic)
      {
         ticket = t; type = PositionGetInteger(POSITION_TYPE);
         open = PositionGetDouble(POSITION_PRICE_OPEN);
         sl = PositionGetDouble(POSITION_SL); tp = PositionGetDouble(POSITION_TP);
         opened = (datetime)PositionGetInteger(POSITION_TIME);
         vol = PositionGetDouble(POSITION_VOLUME);
         return true;
      }
   }
   return false;
}

int DeletePending()
{
   int n = 0;
   for(int i = OrdersTotal() - 1; i >= 0; i--)
   {
      ulong t = OrderGetTicket(i);
      if(t && OrderGetString(ORDER_SYMBOL) == _Symbol && OrderGetInteger(ORDER_MAGIC) == Magic)
      { trade.OrderDelete(t); n++; }
   }
   return n;
}

int PendingCount()
{
   int n = 0;
   for(int i = OrdersTotal() - 1; i >= 0; i--)
   {
      ulong t = OrderGetTicket(i);
      if(t && OrderGetString(ORDER_SYMBOL) == _Symbol && OrderGetInteger(ORDER_MAGIC) == Magic) n++;
   }
   return n;
}

//+------------------------------------------------------------------+
void ManagePosition()
{
   ulong t; long type; double open, sl, tp, vol; datetime opened;
   if(!GetPosition(t, type, open, sl, tp, opened, vol)) return;

   if(MaxHoldHours > 0 && TimeCurrent() - opened >= MaxHoldHours * 3600)
   {
      trade.PositionClose(t);
      return;
   }
   if(!PartialAtMid || g_partialDone || g_mid <= 0) return;
   double bid = SymbolInfoDouble(_Symbol, SYMBOL_BID), ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK);
   bool reached = (type == POSITION_TYPE_BUY) ? bid >= g_mid : ask <= g_mid;
   if(!reached) return;
   double half = NormalizeLot(vol / 2);
   g_partialDone = true;
   if(half < vol && trade.PositionClosePartial(t, half))
      trade.PositionModify(t, NormalizeDouble(open, _Digits), tp);
   else
      trade.PositionModify(t, NormalizeDouble(open, _Digits), tp);   // too small to split: breakeven only
}

//+------------------------------------------------------------------+
void OnTick()
{
   ManagePosition();
   if(g_orderExpiry > 0 && TimeCurrent() >= g_orderExpiry) { DeletePending(); g_orderExpiry = 0; }

   static datetime lastBar = 0;
   datetime bar0 = iTime(_Symbol, PERIOD_M5, 0);
   if(bar0 == lastBar) return;
   lastBar = bar0;

   // the 5-min candle that just closed
   datetime t1 = iTime(_Symbol, PERIOD_M5, 1);
   datetime ny1 = ToNY(t1);
   datetime bs = (datetime)(((long)ny1 - 7200) / 14400 * 14400 + 7200);   // NY-aligned 4H block

   if(bs != b_startNY)
   {
      b_startNY = bs; b_swept = 0; b_done = false;
      MqlRates r[];
      datetime from = ToServer(bs) - 4 * 3600, to = ToServer(bs) - 1;
      int cnt = CopyRates(_Symbol, PERIOD_M5, from, to, r);
      if(cnt < 40) { b_done = true; return; }      // weekend / missing data
      b_hi = r[0].high; b_lo = r[0].low;
      for(int i = 1; i < cnt; i++) { b_hi = MathMax(b_hi, r[i].high); b_lo = MathMin(b_lo, r[i].low); }
   }
   if(b_done) return;

   double h1 = iHigh(_Symbol, PERIOD_M5, 1), l1 = iLow(_Symbol, PERIOD_M5, 1), c1 = iClose(_Symbol, PERIOD_M5, 1);

   if(b_swept == 0)
   {
      if(!InKillZone(ny1)) return;
      bool hi = h1 > b_hi, lo = l1 < b_lo;
      if(hi && lo) { b_done = true; return; }
      if(hi)      { b_swept = 1;  b_extreme = h1; b_extremeTime = t1; }
      else if(lo) { b_swept = -1; b_extreme = l1; b_extremeTime = t1; }
      return;
   }
   if((b_swept == 1 && l1 < b_lo) || (b_swept == -1 && h1 > b_hi)) { b_done = true; return; }
   if(b_swept == 1 && h1 > b_extreme)  { b_extreme = h1; b_extremeTime = t1; }
   if(b_swept == -1 && l1 < b_extreme) { b_extreme = l1; b_extremeTime = t1; }
   if(!InKillZone(ny1)) return;

   // structure before the sweep extreme
   int se = iBarShift(_Symbol, PERIOD_M5, b_extremeTime);
   double arr[];
   double level;
   if(b_swept == 1)
   {
      if(CopyLow(_Symbol, PERIOD_M5, se + 1, MSSLookback, arr) != MSSLookback) return;
      level = arr[ArrayMinimum(arr)];
      if(!(c1 < level && c1 < b_hi)) return;
   }
   else
   {
      if(CopyHigh(_Symbol, PERIOD_M5, se + 1, MSSLookback, arr) != MSSLookback) return;
      level = arr[ArrayMaximum(arr)];
      if(!(c1 > level && c1 > b_lo)) return;
   }
   b_done = true;   // one setup per block

   ulong pt; long ptype; double po, psl, ptp, pv; datetime popen;
   if(GetPosition(pt, ptype, po, psl, ptp, popen, pv) || PendingCount() > 0) return;
   if(SymbolInfoInteger(_Symbol, SYMBOL_SPREAD) > MaxSpreadPoints) return;

   bool isLong = (b_swept == -1);
   double stop   = isLong ? b_extreme - StopBuffer : b_extreme + StopBuffer;
   double target = isLong ? b_hi : b_lo;
   double entry  = Retrace > 0 ? c1 + Retrace * (b_extreme - c1)
                               : (isLong ? SymbolInfoDouble(_Symbol, SYMBOL_ASK) : SymbolInfoDouble(_Symbol, SYMBOL_BID));
   double risk   = MathAbs(entry - stop);
   double reward = isLong ? target - entry : entry - target;
   if(risk < MinStop || reward < MinRR * risk) return;

   entry  = NormalizeDouble(entry, _Digits);
   stop   = NormalizeDouble(stop, _Digits);
   target = NormalizeDouble(target, _Digits);
   double lot = CalcLot();
   g_mid = (b_hi + b_lo) / 2;
   g_partialDone = false;

   bool ok;
   if(Retrace > 0)
   {
      datetime exp = TimeCurrent() + ExpiryBars * 300;
      ok = isLong ? trade.BuyLimit (lot, entry, _Symbol, stop, target, ORDER_TIME_GTC, 0, "CRT")
                  : trade.SellLimit(lot, entry, _Symbol, stop, target, ORDER_TIME_GTC, 0, "CRT");
      if(ok) g_orderExpiry = exp;
   }
   else
      ok = isLong ? trade.Buy (lot, _Symbol, 0, stop, target, "CRT")
                  : trade.Sell(lot, _Symbol, 0, stop, target, "CRT");

   PrintFormat("CRT %s %.2f lot @ %.2f SL %.2f TP %.2f (range %.2f-%.2f, %.1fR) %s",
               isLong ? "BUY" : "SELL", lot, entry, stop, target, b_lo, b_hi, reward / risk, ok ? "placed" : "FAILED");
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
   // a breakeven stop after the partial is not a loss
   if(pnl < 0 && HistoryDealGetInteger(trans.deal, DEAL_REASON) == DEAL_REASON_SL && !g_partialDone)
      g_lossMult = MathMax(MinLossFactor, g_lossMult * LossLotFactor);
   else if(pnl > 0)
      g_lossMult = 1.0;
   GlobalVariableSet(gvMult, g_lossMult);
}
//+------------------------------------------------------------------+
