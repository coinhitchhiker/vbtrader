package com.coinhitchhiker.vbtrader.simulator;

import org.joda.time.DateTime;

public class SimulResult {

    private final double version = 1.0D;

    private String SIMUL_START;
    private String SIMUL_END;
    private int MA_MIN;
    private int TRADING_WINDOW_SIZE_IN_MIN;
    private int TRADING_WINDOW_LOOK_BEHIND;
    private double START_USD_BALANCE;
    private double END_USD_BALANCE;
    private double PRICE_MA_WEIGHT;
    private double VOLUME_MA_WEIGHT;
    private double SLIPPAGE;
    private double H_VOLUME_WEIGHT;

    public double getVersion() {
        return version;
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

    public double getH_VOLUME_WEIGHT() {
        return H_VOLUME_WEIGHT;
    }

    public void setH_VOLUME_WEIGHT(double h_VOLUME_WEIGHT) {
        H_VOLUME_WEIGHT = h_VOLUME_WEIGHT;
    }
}
