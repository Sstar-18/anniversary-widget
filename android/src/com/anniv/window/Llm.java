package com.anniv.window;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 每日一句：智谱 GLM-4-Flash，失败时用内置的节气文案兜底。 */
public class Llm {

    public static final String K_KEY = "apiKey";
    public static final String K_QDATE = "quoteDate";
    public static final String K_QTEXT = "quoteText";

    public static String key(Context c) {
        String k = c.getSharedPreferences(Core.PREFS, 0).getString(K_KEY, "");
        return k == null ? "" : k.trim();
    }

    public static void setKey(Context c, String k) {
        c.getSharedPreferences(Core.PREFS, 0).edit().putString(K_KEY, k.trim()).apply();
    }

    /** 每 5 小时一个时段：0-4 / 5-9 / 10-14 / 15-19 / 20-23 点 */
    private static String today(Context c) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int slot = cal.get(java.util.Calendar.HOUR_OF_DAY) / 5;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.getTime()) + "#" + slot;
    }

    /** 今天这一句（有缓存就先用缓存，没有就用兜底） */
    public static String todayQuote(Context c) {
        String d = c.getSharedPreferences(Core.PREFS, 0).getString(K_QDATE, "");
        String t = c.getSharedPreferences(Core.PREFS, 0).getString(K_QTEXT, "");
        if (today(c).equals(d) && t != null && t.length() > 0) return t;
        return fallback(c);
    }

    public static boolean needFetch(Context c) {
        if (key(c).length() < 10) return false;
        String d = c.getSharedPreferences(Core.PREFS, 0).getString(K_QDATE, "");
        return !today(c).equals(d);
    }

    /** 内置兜底文案：不点节气，按日期轮换的一池子话 */
    private static String fallback(Context c) {
        String[] lines = {
                "汤在锅里小火煨着，等你进门。",
                "今天风有点凉，外套留给你了。",
                "灯我留着，你慢慢回来。",
                "日子不算快，刚好够我记住你。",
                "被子晒过了，今晚会很好睡。",
                "好吃的都留了一半给你。",
                "今天没什么大事，就是想你了。",
                "路上慢点，家里不着急。",
                "你笑起来的时候，日子就好过了。",
                "抱一下，就一会儿。",
                "把这一天记下来，因为有你。",
                "晚安，明天也一起。",
                "水开了，先给你倒一杯。",
                "窗外有风，屋里有你，够了。",
                "想买支花给你，又怕你嫌浪费。"
        };
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int doy = cal.get(java.util.Calendar.DAY_OF_YEAR);
        int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
        return lines[(doy + h / 5) % lines.length];
    }

    /** 联网生成一句，成功后写入缓存。返回 "ok" 或失败原因。 */
    public static String fetch(Context c) {
        String k = key(c);
        if (k.length() < 10) return "还没填 API Key";

        List<String[]> items = Core.items(c);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        String part = hour < 6 ? "凌晨" : hour < 11 ? "早上" : hour < 14 ? "中午"
                : hour < 18 ? "下午" : hour < 23 ? "晚上" : "深夜";
        String tRaw = Weather.temp(c);
        String fest = "";
        try {
            double t = Double.parseDouble(tRaw.replace("°", "").trim());
            fest = t <= 3 ? "很冷" : t <= 10 ? "冷" : t <= 16 ? "偏凉"
                    : t <= 24 ? "不冷不热" : t <= 30 ? "偏热" : "很热";
        } catch (Throwable ignored) { }

        StringBuilder ctx = new StringBuilder();
        ctx.append("今天是").append(new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date()));
        ctx.append("，节气「").append(Core.currentTerm()).append("」");
        ctx.append("，我们在一起第").append(Core.daysTogether(c)).append("天");
        if (items.size() > 0) {
            ctx.append("，最近要过的日子是「").append(items.get(0)[0]).append("」（还有")
                    .append(items.get(0)[1]).append("）");
        }
        if (!"".equals(fest)) {
            ctx.append("；此刻").append(part).append("，").append(Weather.shortPlace(c)).append(" ")
                    .append(tRaw).append("°").append(Weather.desc(c)).append("，体感").append(fest);
        }

        String body = "{\"model\":\"glm-4-flash\",\"temperature\":0.85,\"max_tokens\":120,"
                + "\"messages\":[{\"role\":\"user\",\"content\":"
                + quote(ctx.toString()) + "}]}";

        HttpURLConnection conn = null;
        try {
            URL u = new URL("https://open.bigmodel.cn/api/paas/v4/chat/completions");
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + k);

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = pickMessage(readAll(conn.getErrorStream()));
                return "接口返回 " + code + (err == null ? "" : "：" + err);
            }

            String text = pick(readAll(conn.getInputStream()));
            if (text == null) return "返回内容看不懂，检查 Key 和模型名";
            text = text.replace("\\n", " ").replace("\"", "").trim();
            if (text.length() > 26) text = text.substring(0, 26) + "…";

            c.getSharedPreferences(Core.PREFS, 0).edit()
                    .putString(K_QDATE, today(c))
                    .putString(K_QTEXT, text)
                    .apply();
            return "ok";
        } catch (Exception e) {
            return "网络不通（" + e.getClass().getSimpleName() + "）";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String quote(String ctx) {
        String p = "你在替我给恋人写一句放在手机桌面上的话。风格要像我本人在说的那种，有格调，不油腻，不文学腔。\n"
                + "背景（只作参考，不必写进句子）：" + ctx + "。\n"
                + "只写一句，不超过24个字。要求：\n"
                + "1. 不要提节气名字（右上角已经在显示），也不要报温度数字；\n"
                + "2. 不写“愿你”“希望”“祝福”“亲爱的”这类套话，不排比，不堆形容词；\n"
                + "3. 可以借天气和季节的气息做意象（风、灯、夜、凉、热茶、晒过的被子），但要具体、克制、留一点余味；\n"
                + "4. 第一人称，或直接对她说话，像一句日记、一条没头没尾的微信；\n"
                + "5. 不要引号、不要emoji、不要解释，只输出这一句话。";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < p.length(); i++) {
            char ch = p.charAt(i);
            if (ch == '"') sb.append("\\\"");
            else if (ch == '\\') sb.append("\\\\");
            else if (ch == '\n') sb.append("\\n");
            else sb.append(ch);
        }
        return sb.append("\"").toString();
    }

    private static String readAll(java.io.InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    /** 错误体里的 message 字段 */
    private static String pickMessage(String json) {
        if (json == null) return null;
        int i = json.indexOf("\"message\"");
        if (i < 0) return null;
        int q = json.indexOf('"', i + 9);
        if (q < 0) return null;
        int e = json.indexOf('"', q + 1);
        if (e < 0) return null;
        return json.substring(q + 1, e);
    }

    /** 从返回 JSON 里抠出 choices[0].message.content */
    private static String pick(String json) {
        try {
            int ci = json.indexOf("\"content\"");
            if (ci < 0) return null;
            int q = json.indexOf("\"", ci + 9);
            if (q < 0) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = q + 1; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '\\') {
                    char nx = json.charAt(i + 1);
                    if (nx == 'n') sb.append(' ');
                    else if (nx == 'u') {
                        sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                        i += 4;
                    } else sb.append(nx);
                    i++;
                } else if (ch == '"') break;
                else sb.append(ch);
            }
            String s = sb.toString().trim();
            return s.length() == 0 ? null : s;
        } catch (Exception e) {
            return null;
        }
    }
}
