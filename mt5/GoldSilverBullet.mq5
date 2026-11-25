//+------------------------------------------------------------------+
//| GoldSilverBullet.mq5                                              |
//| "First FVG after a sweep" in the Silver Bullet windows, XAUUSD M5 |
//|                                                                   |
//| Windows (New York time): London 03-04, NY AM 10-11, NY PM 14-15.  |
//| Liquidity = high/low of the LookbackHours before each window.     |
//| Sweep     = a 5-min candle in the window trades beyond that       |
//|             high (-> shorts) or low (-> longs).                   |
//| Entry     = limit at the middle (CE) of the first opposite FVG    |
//|             formed after the sweep; cancelled when window closes. |
//| Stop      = beyond the sweep extreme + buffer.                    |
//| Target    = opposite liquidity level (or fixed RR).               |
//| Lots      = 0.01 start, compounding with balance, cut after SL.   |
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

input group "Sessions (New York time)"
input int    NYOffsetHours  = 7;     // Broker server time minus New York time (most brokers: 7)
input bool   TradeLondon    = true;  // 03:00-04:00
input bool   TradeNYAM      = true;  // 10:00-11:00
input bool   TradeNYPM      = true;  // 14:00-15:00
input int    CloseHourNY    = 16;    // Close anything still open at this NY hour

input group "Setup"
input double LookbackHours  = 3;     // Hours before the window that define liquidity
input bool   TargetLiquidity = true; // true: TP at opposite liquidity, false: fixed RR
input double RR             = 2.0;   // Fixed RR when TargetLiquidity = false
input double MinRR          = 1.0;   // Skip if the liquidity target is closer than this
input double StopBuffer     = 0.10;  // $ beyond the sweep extreme
input double MinStop        = 1.0;   // Skip setups with a stop smaller than this ($)
input int    MaxSpreadPoints = 40;
input long   Magic          = 20261125;

CTrade trade;
double g_startBalance = 0, g_lossMult = 1.0;
string gvStart, gvMult;

// per-window state
int      w_id = -1;         // yyyymmdd*10 + window index
double   w_liqHi, w_liqLo, w_extreme;
int      w_swept;           // 0 none, 1 high swept, -1 low swept
datetime w_sweepTime, w_endServer;
bool     w_done;

//+------------------------------------------------------------------+
int OnInit()
{
   if(_Period != PERIOD_M5) Print("Note: signals use M5 candles regardless of chart timeframe");
   trade.SetExpertMagicNumber(Magic);
   trade.SetTypeFillingBySymbol(_Symbol);
   gvStart = StringFormat("SB_%s_%I64d_start", _Symbol, Magic);
   gvMult  = StringFormat("SB_%s_%I64d_mult",  _Symbol, Magic);
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

datetime ToNY(datetime server)  { return server - NYOffsetHours * 3600; }
datetime ToServer(datetime ny)  { return ny + NYOffsetHours * 3600; }

// which window a NY time is in: 0 London, 1 NY AM, 2 NY PM, -1 none
int WindowOf(datetime ny, int &startHour)
{
   MqlDateTime t; TimeToStruct(ny, t);
   if(TradeLondon && t.hour == 3)  { startHour = 3;  return 0; }
   if(TradeNYAM   && t.hour == 10) { startHour = 10; return 1; }
   if(TradeNYPM   && t.hour == 14) { startHour = 14; return 2; }
   return -1;
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
   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong t = PositionGetTicket(i);
      if(t && PositionGetString(POSITION_SYMBOL) == _Symbol && PositionGetInteger(POSITION_MAGIC) == Magic)
         trade.PositionClose(t);
   }
}

//+------------------------------------------------------------------+
void OnTick()
{
   static datetime lastBar = 0;
   datetime bar0 = iTime(_Symbol, PERIOD_M5, 0);
   if(bar0 == lastBar) return;
   lastBar = bar0;

   datetime nowNY = ToNY(bar0);
   MqlDateTime n; TimeToStruct(nowNY, n);

   // housekeeping: window over -> cancel unfilled order; end of day -> flat
   if(w_id >= 0 && bar0 >= w_endServer) DeletePending();
   if(n.hour == CloseHourNY && n.min == 0) { DeletePending(); CloseAll(); }

   // evaluate the candle that just closed
   datetime b1 = iTime(_Symbol, PERIOD_M5, 1);
   datetime ny1 = ToNY(b1);
   int sh;
   int w = WindowOf(ny1, sh);
   if(w < 0) return;

   MqlDateTime d; TimeToStruct(ny1, d);
   int id = (d.year * 10000 + d.mon * 100 + d.day) * 10 + w;
   if(id != w_id)
   {
      // new window: liquidity from the lookback period
      datetime wsNY = StringToTime(StringFormat("%04d.%02d.%02d %02d:00", d.year, d.mon, d.day, sh));
      datetime ws = ToServer(wsNY);
      MqlRates r[];
      int cnt = CopyRates(_Symbol, PERIOD_M5, ws - (int)(LookbackHours * 3600), ws - 1, r);
      w_id = id; w_swept = 0; w_done = false; w_endServer = ws + 3600;
      if(cnt < LookbackHours * 12 * 0.8) { w_done = true; return; }
      w_liqHi = r[0].high; w_liqLo = r[0].low;
      for(int i = 1; i < cnt; i++) { w_liqHi = MathMax(w_liqHi, r[i].high); w_liqLo = MathMin(w_liqLo, r[i].low); }
   }
   if(w_done) return;

   double h1 = iHigh(_Symbol, PERIOD_M5, 1), l1 = iLow(_Symbol, PERIOD_M5, 1);
   if(w_swept == 0)
   {
      if(h1 > w_liqHi)      { w_swept = 1;  w_extreme = h1; w_sweepTime = b1; }
      else if(l1 < w_liqLo) { w_swept = -1; w_extreme = l1; w_sweepTime = b1; }
      return;
   }
   w_extreme = (w_swept == 1) ? MathMax(w_extreme, h1) : MathMin(w_extreme, l1);

   // FVG: candle 3 = c1, candle 1 = c3, c1 must be at/after the sweep candle
   if(iTime(_Symbol, PERIOD_M5, 3) < w_sweepTime) return;
   double c1h = iHigh(_Symbol, PERIOD_M5, 3), c1l = iLow(_Symbol, PERIOD_M5, 3);
   bool bearFVG = (w_swept == 1)  && c1l > h1;
   bool bullFVG = (w_swept == -1) && c1h < l1;
   if(!bearFVG && !bullFVG) return;
   w_done = true;   // only the first FVG counts

   if(SymbolInfoInteger(_Symbol, SYMBOL_SPREAD) > MaxSpreadPoints) return;

   double entry, sl, tp, risk, reward;
   if(bullFVG)
   {
      entry = (c1h + l1) / 2;
      sl    = w_extreme - StopBuffer;
      risk  = entry - sl;
      tp    = TargetLiquidity ? w_liqHi : entry + RR * risk;
      reward = tp - entry;
   }
   else
   {
      entry = (c1l + h1) / 2;
      sl    = w_extreme + StopBuffer;
      risk  = sl - entry;
      tp    = TargetLiquidity ? w_liqLo : entry - RR * risk;
      reward = entry - tp;
   }
   if(risk < MinStop || reward < MinRR * risk) return;

   entry = NormalizeDouble(entry, _Digits);
   sl    = NormalizeDouble(sl, _Digits);
   tp    = NormalizeDouble(tp, _Digits);
   double lot = CalcLot();
   bool ok = bullFVG
      ? trade.BuyLimit (lot, entry, _Symbol, sl, tp, ORDER_TIME_SPECIFIED, w_endServer, "SilverBullet")
      : trade.SellLimit(lot, entry, _Symbol, sl, tp, ORDER_TIME_SPECIFIED, w_endServer, "SilverBullet");
   if(!ok)  // some brokers reject expiry times: place GTC, it is cancelled at window end anyway
      ok = bullFVG ? trade.BuyLimit (lot, entry, _Symbol, sl, tp)
                   : trade.SellLimit(lot, entry, _Symbol, sl, tp);
   PrintFormat("%s limit %.2f lot @ %.2f SL %.2f TP %.2f (%.1fR) %s",
               bullFVG ? "BUY" : "SELL", lot, entry, sl, tp, reward / risk, ok ? "placed" : "FAILED");
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
