package com.coinhitchhiker.vbtrader.simulator;

public class TopSimulResult {

    private Integer simulResultId;
    private Integer periodId;
    private String simulResult;

    public Integer getSimulResultId() {
        return simulResultId;
    }

    public void setSimulResultId(Integer simulResultId) {
        this.simulResultId = simulResultId;
    }

    public Integer getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Integer periodId) {
        this.periodId = periodId;
    }

    public String getSimulResult() {
        return simulResult;
    }

    public void setSimulResult(String simulResult) {
        this.simulResult = simulResult;
    }

    @Override
    public String toString() {
        return "TopSimulResult{" +
                "simulResultId=" + simulResultId +
                ", periodId=" + periodId +
                ", simulResult='" + simulResult + '\'' +
                '}';
    }
}
