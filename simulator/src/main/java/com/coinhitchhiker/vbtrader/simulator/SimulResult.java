package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.StrategyEnum;
import com.coinhitchhiker.vbtrader.common.model.TradingMode;

public class SimulResult {

    private final double version = 1.3D;

    private Integer periodId;

    private String SIMUL_START;
    private String SIMUL_END;
    private double WINNING_RATE;
    private double START_USD_BALANCE;
    private double END_USD_BALANCE;
    private int TRADING_WINDOW_SIZE_IN_MIN;
    private int TRADING_WINDOW_LOOK_BEHIND;
    private double PRICE_MA_WEIGHT;
    private double VOLUME_MA_WEIGHT;
    private int MA_MIN;
    private double SLIPPAGE;
    private double TS_TRIGGER_PCT;
    private double TS_PCT;
    private TradingMode MODE;
    private int MIN_CANDLE_LOOK_BEHIND;
    private double PVTOBV_DROP_THRESHOLD;
    private double PRICE_DROP_THRESHOLD;
    private double STOP_LOSS_PCT;
    private double IBS_LOWER_THRESHOLD;
    private double IBS_UPPER_THRESHOLD;
    private StrategyEnum STRATEGY;
    private double PL_RATIO;

    public double getVersion() {
        return version;
    }

    public Integer getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Integer periodId) {
        this.periodId = periodId;
    }

    public String getSIMUL_START() {
        return SIMUL_START;
    }

    public void setSIMUL_START(String SIMUL_START) {
        this.SIMUL_START = SIMUL_START;
    }

    public String getSIMUL_END() {
        return SIMUL_END;
    }

    public void setSIMUL_END(String SIMUL_END) {
        this.SIMUL_END = SIMUL_END;
    }

    public int getMA_MIN() {
        return MA_MIN;
    }

    public void setMA_MIN(int MA_MIN) {
        this.MA_MIN = MA_MIN;
    }

    public int getTRADING_WINDOW_SIZE_IN_MIN() {
        return TRADING_WINDOW_SIZE_IN_MIN;
    }

    public void setTRADING_WINDOW_SIZE_IN_MIN(int TRADING_WINDOW_SIZE_IN_MIN) {
        this.TRADING_WINDOW_SIZE_IN_MIN = TRADING_WINDOW_SIZE_IN_MIN;
    }

    public int getTRADING_WINDOW_LOOK_BEHIND() {
        return TRADING_WINDOW_LOOK_BEHIND;
    }

    public void setTRADING_WINDOW_LOOK_BEHIND(int TRADING_WINDOW_LOOK_BEHIND) {
        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
    }

    public double getSTART_USD_BALANCE() {
        return START_USD_BALANCE;
    }

    public void setSTART_USD_BALANCE(double START_USD_BALANCE) {
        this.START_USD_BALANCE = START_USD_BALANCE;
    }

    public double getEND_USD_BALANCE() {
        return END_USD_BALANCE;
    }

    public void setEND_USD_BALANCE(double END_USD_BALANCE) {
        this.END_USD_BALANCE = END_USD_BALANCE;
    }

    public double getPRICE_MA_WEIGHT() {
        return PRICE_MA_WEIGHT;
    }

    public void setPRICE_MA_WEIGHT(double PRICE_MA_WEIGHT) {
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
    }

    public double getVOLUME_MA_WEIGHT() {
        return VOLUME_MA_WEIGHT;
    }

    public void setVOLUME_MA_WEIGHT(double VOLUME_MA_WEIGHT) {
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;
    }

    public double getSLIPPAGE() {
        return SLIPPAGE;
    }

    public void setSLIPPAGE(double SLIPPAGE) {
        this.SLIPPAGE = SLIPPAGE;
    }

    public double getWINNING_RATE() {
        return WINNING_RATE;
    }

    public void setWINNING_RATE(double WINNING_RATE) {
        this.WINNING_RATE = WINNING_RATE;
    }

    public double getTS_TRIGGER_PCT() {
        return TS_TRIGGER_PCT;
    }

    public void setTS_TRIGGER_PCT(double TS_TRIGGER_PCT) {
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
    }

    public double getTS_PCT() {
        return TS_PCT;
    }

    public void setTS_PCT(double TS_PCT) {
        this.TS_PCT = TS_PCT;
    }

    public TradingMode getMODE() {
        return MODE;
    }

    public void setMODE(TradingMode MODE) {
        this.MODE = MODE;
    }

    public int getMIN_CANDLE_LOOK_BEHIND() {
        return MIN_CANDLE_LOOK_BEHIND;
    }

    public void setMIN_CANDLE_LOOK_BEHIND(int MIN_CANDLE_LOOK_BEHIND) {
        this.MIN_CANDLE_LOOK_BEHIND = MIN_CANDLE_LOOK_BEHIND;
    }

    public double getPVTOBV_DROP_THRESHOLD() {
        return PVTOBV_DROP_THRESHOLD;
    }

    public void setPVTOBV_DROP_THRESHOLD(double PVTOBV_DROP_THRESHOLD) {
        this.PVTOBV_DROP_THRESHOLD = PVTOBV_DROP_THRESHOLD;
    }

    public double getPRICE_DROP_THRESHOLD() {
        return PRICE_DROP_THRESHOLD;
    }

    public void setPRICE_DROP_THRESHOLD(double PRICE_DROP_THRESHOLD) {
        this.PRICE_DROP_THRESHOLD = PRICE_DROP_THRESHOLD;
    }

    public double getSTOP_LOSS_PCT() {
        return STOP_LOSS_PCT;
    }

    public void setSTOP_LOSS_PCT(double STOP_LOSS_PCT) {
        this.STOP_LOSS_PCT = STOP_LOSS_PCT;
    }

    public double getIBS_LOWER_THRESHOLD() {
        return IBS_LOWER_THRESHOLD;
    }

    public void setIBS_LOWER_THRESHOLD(double IBS_LOWER_THRESHOLD) {
        this.IBS_LOWER_THRESHOLD = IBS_LOWER_THRESHOLD;
    }

    public double getIBS_UPPER_THRESHOLD() {
        return IBS_UPPER_THRESHOLD;
    }

    public void setIBS_UPPER_THRESHOLD(double IBS_UPPER_THRESHOLD) {
        this.IBS_UPPER_THRESHOLD = IBS_UPPER_THRESHOLD;
    }

    public StrategyEnum getSTRATEGY() {
        return STRATEGY;
    }

    public void setSTRATEGY(StrategyEnum STRATEGY) {
        this.STRATEGY = STRATEGY;
    }

    public double getPL_RATIO() {
        return PL_RATIO;
    }

    public void setPL_RATIO(double PL_RATIO) {
        this.PL_RATIO = PL_RATIO;
    }
}
