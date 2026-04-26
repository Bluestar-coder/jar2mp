package com.z0fsec.jar2mp.util;

import java.lang.management.ManagementFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    public static String timestampToDate(Long timestamp) {
        return timestampToDate(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    public static String timestampToDate(String timestamp) {
        return timestampToDate(Long.parseLong(timestamp), "yyyy-MM-dd HH:mm:ss");
    }

    public static String timestampToDate(Long timestamp, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(new Date(timestamp * 1000));
    }

    public static String timestampToDate(String timestamp, String format) {
        return timestampToDate(Long.parseLong(timestamp), format);
    }

    public static Long dateToTimestamp(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(dateStr).getTime() / 1000;
        } catch (ParseException e) {
            return 0L;
        }
    }

    public static String millisecondToDate(Long millisecond) {
        return millisecondToDate(millisecond, "yyyy-MM-dd HH:mm:ss");
    }

    public static String millisecondToDate(Long millisecond, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(new Date(millisecond));
    }

    public static String formatDate(String dateStr) {
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm",
                "yyyy-MM-dd", "yyyy/MM/dd",
                "yyyyMMddHHmmss", "yyyyMMdd",
                "MM/dd/yyyy HH:mm:ss", "MM-dd-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss",
                "yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p);
                sdf.setLenient(false);
                Date date = sdf.parse(dateStr);
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
            } catch (ParseException ignored) {
            }
        }
        return dateStr;
    }

    public static long timestamp() {
        return System.currentTimeMillis() / 1000;
    }

    public static Date nowDate() {
        return new Date();
    }

    public static List<Long> today() {
        return dayRange(0);
    }

    public static List<Long> yesterday() {
        return dayRange(-1);
    }

    public static List<Long> week() {
        return dayRange(-7);
    }

    public static List<Long> lastWeek() {
        return dayRange(-14);
    }

    public static List<Long> month() {
        return dayRange(-30);
    }

    public static List<Long> lastMonth() {
        return dayRange(-60);
    }

    public static List<Long> year() {
        return dayRange(-365);
    }

    public static List<Long> lastYear() {
        return dayRange(-730);
    }

    private static List<Long> dayRange(int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start;
        if (offsetDays >= 0) {
            start = cal.getTimeInMillis() / 1000;
        } else {
            cal.add(Calendar.DAY_OF_MONTH, offsetDays);
            start = cal.getTimeInMillis() / 1000;
        }
        long end = System.currentTimeMillis() / 1000;
        return Arrays.asList(start, end);
    }

    public static int dayOfWeek() {
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    public static int monthDay() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    public static long serverStartDate() {
        return ManagementFactory.getRuntimeMXBean().getStartTime() / 1000;
    }

    public static String datePoor(Date startDate, Date endDate) {
        long diff = endDate.getTime() - startDate.getTime();
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);
        return days + "d " + hours + "h " + minutes + "m";
    }

    public static long daysAgo(long days) {
        return System.currentTimeMillis() / 1000 - days * 24 * 60 * 60;
    }

    public static long daysAfter(long days) {
        return System.currentTimeMillis() / 1000 + days * 24 * 60 * 60;
    }
}
