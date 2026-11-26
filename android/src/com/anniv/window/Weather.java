package com.anniv.window;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** 天气：Open-Meteo，无需密钥；结果缓存在 SharedPreferences，小组件只读缓存。 */
public final class Weather {

    private static volatile boolean fetching = false;

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("anniv", Context.MODE_PRIVATE);
    }

    public static String city(Context c) {
        String s = sp(c).getString("city", "");
        return (s == null || s.trim().isEmpty()) ? "青岛" : s.trim();
    }

    public static String temp(Context c) {
        String s = sp(c).getString("w_temp", "");
        return (s == null || s.isEmpty()) ? "--" : s;
    }

    public static String desc(Context c) {
        String s = sp(c).getString("w_desc", "");
        return (s == null || s.isEmpty()) ? "天气" : s;
    }

    public static int kind(Context c) {
        return sp(c).getInt("w_kind", -1);
    }

    /** 缓存超过 20 分钟就在后台刷新一次；完成后通知小组件重画。 */
    public static void refreshIfStale(final Context app) {
        long ts = sp(app).getLong("w_ts", 0);
        boolean staleGeo = sp(app).getInt("geo_v", 0) != 3;   // 老坐标作废，强制重查
        if (!staleGeo && System.currentTimeMillis() - ts < 10L * 60L * 1000L) return;
        if (fetching) return;
        fetching = true;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try { ok = fetch(app); } catch (Throwable ignored) { }
                fetching = false;
                if (!ok) return;
                try {
                    AppWidgetManager mgr = AppWidgetManager.getInstance(app);
                    int[] ids = mgr.getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
                    for (int id : ids) WidgetProvider.update(app, mgr, id);
                } catch (Throwable ignored) { }
            }
        }).start();
    }

    /** 手动刷新：清掉时间戳后立刻再抓一次 */
    public static void force(final Context app) {
        sp(app).edit().putLong("w_ts", 0).apply();
        refreshIfStale(app);
    }

    /** 上次取到温度的时间，给设置页看新鲜度 */
    public static String stamp(Context c) {
        long ts = sp(c).getLong("w_ts", 0);
        if (ts <= 0) return "还没取过";
        long min = (System.currentTimeMillis() - ts) / 60000L;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
        if (min <= 0) return "刚更新 " + f.format(new java.util.Date(ts));
        return f.format(new java.util.Date(ts)) + " 更新，已过 " + min + " 分钟";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "AnnivWidget/1.9");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();
        return sb.toString();
    }

    /** 数据源：0 国内（中国天气网）1 国际（Open-Meteo） */
    public static int source(Context c) {
        return sp(c).getInt("w_src", 0);
    }

    public static void setSource(Context c, int v) {
        sp(c).edit().putInt("w_src", v).putLong("w_ts", 0L).apply();
    }

    /** 中国天气网要求带 Referer */
    private static String getRef(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Referer", "https://www.weather.com.cn/");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();
        return sb.toString();
    }

    private static boolean fetch(Context ctx) throws Exception {
        SharedPreferences p = sp(ctx);
        String city = city(ctx);
        if (source(ctx) == 0) {
            try {
                if (fetchCn(ctx, p, city)) return true;
            } catch (Throwable ignored) { }
        }
        double lat, lon;
        boolean useLoc = p.getBoolean("use_loc", false) && p.getFloat("fix_lat", 0f) != 0f;
        if (useLoc) {
            lat = p.getFloat("fix_lat", 0f);
            lon = p.getFloat("fix_lon", 0f);
        } else if (p.getInt("geo_v", 0) == 3 && city.equals(p.getString("geo_city", "")) && p.getFloat("lat", 0f) != 0f) {
            lat = p.getFloat("lat", 0f);
            lon = p.getFloat("lon", 0f);
        } else {
            JSONObject g = geocode(city);
            if (g == null) return false;
            lat = g.getDouble("latitude");
            lon = g.getDouble("longitude");
            p.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon)
                    .putString("geo_city", city).putInt("geo_v", 3)
                    .putString("w_place", placeName(g)).apply();
        }

        String js = get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&cell_selection=nearest&timezone=auto");
        JSONObject cur = new JSONObject(js).getJSONObject("current");
        double t = cur.getDouble("temperature_2m");
        int code = cur.getInt("weather_code");

        String[] d = describe(code);
        p.edit()
                .putString("w_temp", String.valueOf(Math.round(t)))
                .putString("w_desc", d[0])
                .putInt("w_kind", Integer.parseInt(d[1]))
                .putString("w_city", city)
                .putLong("w_ts", System.currentTimeMillis())
                .apply();
        return true;
    }

    /** 中国天气网：免密钥，先按地名换城市码，再取实时数据 */
    private static boolean fetchCn(Context ctx, SharedPreferences p, String city) throws Exception {
        String name = city;
        if (locationMode(ctx)) {
            String shortName = shortPlace(ctx);
            if (!shortName.isEmpty()) name = shortName;
        }
        String code = cnCode(name);
        if (code == null) code = cnCode(name.replaceAll("(市|区|县)$", ""));
        if (code == null) return false;
        String body = getRef("https://d1.weather.com.cn/sk_2d/" + code + ".html");
        int a = body.indexOf('{'), b = body.lastIndexOf('}');
        if (a < 0 || b <= a) return false;
        JSONObject o = new JSONObject(body.substring(a, b + 1));
        String t = o.optString("temp", "");
        if (t.isEmpty()) return false;
        String weather = o.optString("weather", "多云");
        String place = o.optString("cityname", name);
        int kind = kindOf(weather);
        p.edit()
                .putString("w_temp", String.valueOf(Math.round(Double.parseDouble(t.trim()))))
                .putString("w_desc", weather)
                .putInt("w_kind", kind)
                .putString("w_city", city)
                .putString("w_place", place)
                .putLong("w_ts", System.currentTimeMillis())
                .apply();
        return true;
    }

    /** 地名 → 101 开头的城市码；景点码带 A，跳过 */
    private static String cnCode(String name) throws Exception {
        String js = getRef("https://toy1.weather.com.cn/search?cityname="
                + URLEncoder.encode(name, "UTF-8"));
        int from = 0;
        while (true) {
            int i = js.indexOf("\"ref\":\"", from);
            if (i < 0) return null;
            int j = js.indexOf('~', i);
            if (j < 0) return null;
            String code = js.substring(i + 7, j);
            from = j;
            if (code.startsWith("101") && code.indexOf('A') < 0 && code.length() >= 9) return code;
        }
    }

    private static int kindOf(String w) {
        if (w.contains("雷")) return 4;
        if (w.contains("雪")) return 3;
        if (w.contains("雨")) return 2;
        if (w.contains("雾") || w.contains("霾") || w.contains("沙") || w.contains("尘")) return 5;
        if (w.contains("晴")) return 0;
        return 1;
    }

    /** @return {文字描述, 图标类型} */
    private static String[] describe(int c) {
        if (c == 0) return new String[]{"晴", "0"};
        if (c == 1) return new String[]{"晴间多云", "1"};
        if (c == 2) return new String[]{"多云", "1"};
        if (c == 3) return new String[]{"阴", "1"};
        if (c == 45 || c == 48) return new String[]{"雾", "5"};
        if (c >= 51 && c <= 57) return new String[]{"细雨", "2"};
        if (c == 61 || c == 80) return new String[]{"小雨", "2"};
        if (c == 63 || c == 81) return new String[]{"中雨", "2"};
        if (c == 65 || c == 82) return new String[]{"大雨", "2"};
        if (c == 66 || c == 67) return new String[]{"冻雨", "2"};
        if (c == 71 || c == 85) return new String[]{"小雪", "3"};
        if (c == 73) return new String[]{"中雪", "3"};
        if (c == 75 || c == 77 || c == 86) return new String[]{"大雪", "3"};
        if (c >= 95) return new String[]{"雷阵雨", "4"};
        return new String[]{"多云", "1"};
    }

    /** 中文地名容错：原样 / 补「市、区、县」都查一遍，命中多选人口最大的，避免同名小村。 */
    private static JSONObject geocode(String city) throws Exception {
        String[] cands = {city, city + "市", city + "区", city + "县"};
        JSONObject best = null, firstExact = null;
        double bestPop = 0;
        for (String q : cands) {
            String js = get("https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(q, "UTF-8") + "&count=8&language=zh&format=json");
            JSONObject o = new JSONObject(js);
            if (!o.has("results")) continue;
            org.json.JSONArray arr = o.getJSONArray("results");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                String nm = r.optString("name", "");
                if (!nm.startsWith(city.replaceAll("(市|区|县)$", ""))) continue;
                if (firstExact == null) firstExact = r;
                double pop = r.optDouble("population", 0);
                if (pop > bestPop) { bestPop = pop; best = r; }
            }
        }
        return best != null ? best : firstExact;
    }

    /** 记下手机定位结果，之后天气直接按坐标取，不再猜地名。 */
    public static void setFix(Context c, double lat, double lon, String place, String shortName) {
        sp(c).edit()
                .putBoolean("use_loc", true)
                .putFloat("fix_lat", (float) lat)
                .putFloat("fix_lon", (float) lon)
                .putString("w_place", place)
                .putString("w_short", shortName)
                .putLong("w_ts", 0L)
                .apply();
    }


    /** 区县名，例如「市南」，用于小组件天气行。 */
    public static String shortPlace(Context c) {
        String s = sp(c).getString("w_short", "");
        return (s == null || s.isEmpty()) ? "" : s;
    }

    public static boolean locationMode(Context c) {
        return sp(c).getBoolean("use_loc", false);
    }

    public static void manualCity(Context c, String city) {
        sp(c).edit().putBoolean("use_loc", false).putString("city", city)
                .putString("geo_city", "").putInt("geo_v", 3).putLong("w_ts", 0L).apply();
    }

    /** 当前天气地点的中文名，用于设置页显示，便于核对。 */
    public static String place(Context c) {
        String s = sp(c).getString("w_place", "");
        return (s == null || s.isEmpty()) ? "" : s;
    }

    private static String placeName(JSONObject g) {
        String admin = g.optString("admin1", "");
        String nm = g.optString("name", "");
        return admin.isEmpty() ? nm : admin + " " + nm;
    }

    /** 用坐标反查中文地名（BigDataCloud，免密钥）。返回 {省 市 区, 区名简称}。 */
    public static String[] reverseGeocode(double lat, double lon) {
        try {
            String js = get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat
                    + "&longitude=" + lon + "&localityLanguage=zh-Hans");
            JSONObject o = new JSONObject(js);
            String prov = o.optString("principalSubdivision", "");
            String cityName = o.optString("city", "");
            String loc = o.optString("locality", "");
            if (cityName.isEmpty()) cityName = o.optString("principalSubdivisionCode", "");
            String full = (prov + " " + cityName + " " + loc).trim().replace("  ", " ");
            String shortName = loc.isEmpty() ? cityName : loc;
            shortName = shortName.replaceAll("(区|县|市)$", "");
            return new String[]{full, shortName};
        } catch (Throwable e) {
            return new String[]{"", ""};
        }
    }
}
