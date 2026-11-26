package com.anniv.window;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 日期与节气计算：不进浏览器、不联网，全部本地算。 */
public class Core {

    public static final String PREFS = "anniv";
    public static final String K_START = "start";

    private static List<Object[]> TERMS; // {name, millis}

    /* ---------------- 配置 ---------------- */

    public static String getStart(Context c) {
        String s = c.getSharedPreferences(PREFS, 0).getString(K_START, "2023-05-20");
        return s == null ? "2023-05-20" : s;
    }

    public static void setStart(Context c, int y, int m, int d) {
        String s = String.format(Locale.CHINA, "%04d-%02d-%02d", y, m, d);
        c.getSharedPreferences(PREFS, 0).edit().putString(K_START, s).apply();
    }

    public static int style(Context c) {
        int s = c.getSharedPreferences(PREFS, 0).getInt("style", 0);
        return s < 0 || s > 3 ? 0 : s;
    }

    public static void setStyle(Context c, int s) {
        c.getSharedPreferences(PREFS, 0).edit().putInt("style", s).apply();
    }

    /** 经典版式（无相框）：true = 回退到相框之前的排版 */
    public static boolean classic(Context c) {
        return c.getSharedPreferences(PREFS, 0).getBoolean("classic", false);
    }

    public static void setClassic(Context c, boolean v) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("classic", v).apply();
    }

    public static List<String[]> extras(Context c) {
        String s = c.getSharedPreferences(PREFS, 0).getString("extras", "");
        List<String[]> out = new ArrayList<String[]>();
        if (s == null || s.length() == 0) {
            out.add(new String[]{"情人节", "02-14"});
            return out;
        }
        for (String row : s.split(";")) {
            int p = row.indexOf('~');
            if (p < 0) continue;
            String n = row.substring(0, p).trim();
            String d = row.substring(p + 1).trim();
            if (d.length() == 5 && d.indexOf('-') == 2) out.add(new String[]{n, d});
        }
        return out;
    }

    public static void setExtras(Context c, List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e[0].replace(";", "").replace("~", "")).append('~').append(e[1]);
        }
        c.getSharedPreferences(PREFS, 0).edit().putString("extras", sb.toString()).apply();
    }

    public static String startText(Context c) {
        String s = getStart(c);
        return s.substring(0, 4) + " 年 " + s.substring(5, 7) + " 月 " + s.substring(8, 10) + " 日";
    }

    /* ---------------- 天数 ---------------- */

    private static Calendar at0(Calendar c) {
        Calendar x = (Calendar) c.clone();
        x.set(Calendar.HOUR_OF_DAY, 0);
        x.set(Calendar.MINUTE, 0);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x;
    }

    private static Calendar start0(Context ctx) {
        String s = getStart(ctx);
        int y = Integer.parseInt(s.substring(0, 4));
        int m = Integer.parseInt(s.substring(5, 7));
        int d = Integer.parseInt(s.substring(8, 10));
        Calendar cal = Calendar.getInstance();
        cal.set(y, m - 1, d, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /* ---------------- 暂停（暂时分开） ---------------- */

    /** 暂停起点（当天零点），0 表示正在计时中 */
    private static long pauseFrom(Context c) {
        return c.getSharedPreferences(PREFS, 0).getLong("pause_from", 0L);
    }

    /** 已冻结掉的总天数（毫秒），恢复时累加 */
    private static long pauseTotal(Context c) {
        return c.getSharedPreferences(PREFS, 0).getLong("pause_total", 0L);
    }

    public static boolean paused(Context c) {
        return pauseFrom(c) > 0;
    }

    /** 计时口径下的"今天"：暂停时退回暂停那天，天数就冻住了 */
    private static long clockNow(Context c) {
        long p = pauseFrom(c);
        return p > 0 ? p : at0(Calendar.getInstance()).getTimeInMillis();
    }

    public static void setPaused(Context c, boolean on) {
        long now = at0(Calendar.getInstance()).getTimeInMillis();
        android.content.SharedPreferences.Editor e = c.getSharedPreferences(PREFS, 0).edit();
        if (on) {
            if (pauseFrom(c) == 0) e.putLong("pause_from", now);
        } else {
            long p = pauseFrom(c);
            if (p > 0 && now > p) e.putLong("pause_total", pauseTotal(c) + (now - p));
            e.putLong("pause_from", 0L);
        }
        e.apply();
    }

    /** 暂停期间冻结的天数（万一在暂停中，也能读出来给设置页看） */
    public static int pausedDays(Context c) {
        long p = pauseFrom(c);
        if (p <= 0) return 0;
        long now = at0(Calendar.getInstance()).getTimeInMillis();
        return (int) Math.round((now - p) / 86400000.0);
    }

    public static int daysTogether(Context ctx) {
        long a = start0(ctx).getTimeInMillis();
        long b = clockNow(ctx); // 暂停时 b 退回暂停那天，天数停住；恢复后按真实日期继续，不扣掉暂停期
        int n = (int) Math.round((b - a) / 86400000.0) + 1;
        return n < 1 ? 0 : n;
    }

    public static String spanText(Context ctx) {
        Calendar a = start0(ctx);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(clockNow(ctx));
        int y = b.get(Calendar.YEAR) - a.get(Calendar.YEAR);
        int m = b.get(Calendar.MONTH) - a.get(Calendar.MONTH);
        int d = b.get(Calendar.DAY_OF_MONTH) - a.get(Calendar.DAY_OF_MONTH);
        if (d < 0) {
            m--;
            Calendar prev = (Calendar) b.clone();
            prev.set(Calendar.DAY_OF_MONTH, 1);
            prev.add(Calendar.DAY_OF_MONTH, -1);
            d += prev.get(Calendar.DAY_OF_MONTH);
        }
        if (m < 0) { y--; m += 12; }
        StringBuilder sb = new StringBuilder();
        if (y > 0) sb.append(y).append(" 年 ");
        if (y > 0 || m > 0) sb.append(m).append(" 个月 ");
        sb.append(d).append(" 天");
        return sb.toString();
    }

    public static String comma(int n) {
        return String.format(Locale.CHINA, "%,d", n);
    }

    /* ---------------- 节气 ---------------- */

    @SuppressWarnings("unchecked")
    public static List<Object[]> terms() {
        if (TERMS != null) return TERMS;
        List<Object[]> list = new ArrayList<Object[]>();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        for (String s : Terms.DATA.split(";")) {
            int p = s.indexOf('|');
            if (p < 0) continue;
            try {
                list.add(new Object[]{s.substring(p + 1), f.parse(s.substring(0, p)).getTime()});
            } catch (Exception ignored) { }
        }
        TERMS = list;
        return list;
    }

    /** 当前节气名（最近一个已交节的） */
    public static String currentTerm() {
        long now = System.currentTimeMillis();
        String name = "立秋";
        for (Object[] t : terms()) {
            if (((Long) t[1]) > now) break;
            name = (String) t[0];
        }
        return name;
    }

    private static final String[] ORDER = {"立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至", "小寒", "大寒"};

    public static int termIndex() {
        String n = currentTerm();
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(n)) return i + 1;
        return 1;
    }

    /** 下一个同名的节气（如立秋、冬至） */
    private static Calendar nextNamedTerm(String name) {
        long today = at0(Calendar.getInstance()).getTimeInMillis();
        for (Object[] t : terms()) {
            if (!name.equals(t[0])) continue;
            long ms = (Long) t[1];
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(ms);
            if (at0(cal).getTimeInMillis() >= today) return cal;
        }
        return null;
    }

    private static final String[] BIG = {"立春", "春分", "立夏", "夏至", "立秋", "秋分", "立冬", "冬至"};

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean isBig(String name) {
        for (String s : BIG) if (s.equals(name)) return true;
        return false;
    }

    /** 跳过某天的那个之后，未来最近的二分二至四立 */
    private static Object[] nextBigSkipping(Calendar skip) {
        long today = at0(Calendar.getInstance()).getTimeInMillis();
        for (Object[] t : terms()) {
            long ms = (Long) t[1];
            if (ms < today) continue;
            String nm = (String) t[0];
            if (!isBig(nm)) continue;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(ms);
            if (skip != null && sameDay(skip, cal)) continue;
            return new Object[]{nm, cal};
        }
        return null;
    }

    /** 未来最近的一个节气；bigOnly 为真时只看二分二至四立 */
    private static Object[] nextTermItem(boolean bigOnly) {
        long today = at0(Calendar.getInstance()).getTimeInMillis();
        for (Object[] t : terms()) {
            long ms = (Long) t[1];
            if (ms < today) continue;
            String nm = (String) t[0];
            if (bigOnly && !isBig(nm)) continue;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(ms);
            return new Object[]{nm, cal};
        }
        return null;
    }

    /* ---------------- 下一件事 ---------------- */

    private static Calendar yearly(int month, int day) {
        Calendar now = at0(Calendar.getInstance());
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        if (c.getTimeInMillis() < now.getTimeInMillis()) c.add(Calendar.YEAR, 1);
        return c;
    }

    /**
     * 返回若干条：{名称, 剩余天数文本, 日期文本}
     * 已按日期升序、同日合并。
     */
    public static List<String[]> items(Context ctx) {
        Calendar now = at0(Calendar.getInstance());
        List<Object[]> raw = new ArrayList<Object[]>();

        Calendar st = start0(ctx);
        Calendar anniv = (Calendar) now.clone();
        anniv.set(Calendar.MONTH, st.get(Calendar.MONTH));
        anniv.set(Calendar.DAY_OF_MONTH, st.get(Calendar.DAY_OF_MONTH));
        if (anniv.getTimeInMillis() < now.getTimeInMillis()) anniv.add(Calendar.YEAR, 1);
        int yrs = anniv.get(Calendar.YEAR) - st.get(Calendar.YEAR);
        raw.add(new Object[]{yrs + " 周年", anniv});

        for (String[] e : extras(ctx)) {
            int mm = Integer.parseInt(e[1].substring(0, 2));
            int dd = Integer.parseInt(e[1].substring(3, 5));
            raw.add(new Object[]{e[0], yearly(mm, dd)});
        }

        Object[] nt = nextTermItem(false);
        if (nt != null) raw.add(new Object[]{nt[0], nt[1]});
        Object[] big = nt == null ? nextTermItem(true) : nextBigSkipping((Calendar) nt[1]);
        if (big != null) raw.add(new Object[]{big[0], big[1]});

        java.util.Collections.sort(raw, new java.util.Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Long.compare(((Calendar) a[1]).getTimeInMillis(), ((Calendar) b[1]).getTimeInMillis());
            }
        });

        List<String[]> out = new ArrayList<String[]>();
        Map<String, Integer> byDay = new HashMap<String, Integer>();
        for (Object[] o : raw) {
            Calendar c = (Calendar) o[1];
            String key = c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DAY_OF_MONTH);
            int left = (int) Math.round((at0(c).getTimeInMillis() - now.getTimeInMillis()) / 86400000.0);
            String dTxt = (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
            if (byDay.containsKey(key)) {
                int i = byDay.get(key);
                out.get(i)[0] = out.get(i)[0] + " · " + o[0];
            } else {
                byDay.put(key, out.size());
                out.add(new String[]{(String) o[0],
                        left <= 0 ? "今天" : (left == 1 ? "明天" : comma(left) + " 天"), dTxt});
            }
        }
        return out;
    }
}
