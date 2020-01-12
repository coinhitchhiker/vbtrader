package com.coinhitchhiker.vbtrader.common.model;

public enum TimeFrame {
    M1, M5, M15, M30, H1, H4, D1, W1;

    public int toSeconds() {
        switch (this) {
            case M1: return 60;
            case M5: return 300;
            case M15: return 900;
            case M30: return 1800;
            case H1: return 3600;
            case H4: return 14400;
            case D1: return 86400;
            case W1: return 86400*7;
            default: throw new IllegalArgumentException("Unreachable code path");
        }
    }
}
