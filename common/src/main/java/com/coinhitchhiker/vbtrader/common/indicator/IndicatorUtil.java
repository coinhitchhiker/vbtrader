package com.coinhitchhiker.vbtrader.common.indicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IndicatorUtil {

    public static List<Double> getLastN(Map<Long, Double> map, int n) {
        List<Double> result = new ArrayList<>();
        int size = map.size();
        int cnt = 0;
        for(Map.Entry<Long, Double> entry : map.entrySet()) {
            if(cnt >= size - n) {
                result.add(entry.getValue());
            }
            cnt++;
        }
        return result;
    }
}
