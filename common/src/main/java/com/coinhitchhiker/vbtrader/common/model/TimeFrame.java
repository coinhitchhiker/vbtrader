package com.coinhitchhiker.vbtrader.common.model;

public enum TimeFrame {
    M1, M2, M3, M4, M5, M15, M30, H1, H12, H4, D1;

    public long toSeconds() {
        switch (this) {
            case M1: return 60;
            case M2: return 120;
            case M3: return 180;
            case M4: return 240;
            case M5: return 300;
            case M15: return 900;
            case M30: return 1800;
            case H1: return 3600;
            case H4: return 14400;
            case H12: return 43200;
            case D1: return 86400;
            default: throw new IllegalArgumentException("Unreachable code path");
        }
    }
}
