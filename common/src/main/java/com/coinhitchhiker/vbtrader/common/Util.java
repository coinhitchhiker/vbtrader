package com.coinhitchhiker.vbtrader.common;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class Util {

    public static DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm, DateTimeZone.UTC);
        return closestMin;
    }

}
