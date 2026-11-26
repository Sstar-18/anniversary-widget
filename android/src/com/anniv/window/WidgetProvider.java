package com.anniv.window;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.os.Build;
import java.util.Calendar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver.PendingResult;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** 桌面小组件：不用打开任何应用，直接挂在桌面上。 */
public class WidgetProvider extends AppWidgetProvider {

    /** 每天零点自己叫醒自己刷新一次；厂商省电策略砍掉 30 分钟定时时的兜底 */
    static final String ACTION_TICK = "com.anniv.window.WIDGET_TICK";
    private static final int ALARM_REQ = 42;

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        final Context app = context.getApplicationContext();
        for (int id : ids) update(app, mgr, id);
        mark(app, "30 分钟定时");
        scheduleNext(app);

        if (Llm.needFetch(app)) {
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                public void run() {
                    try { Llm.fetch(app); } catch (Throwable ignored) { }
                    try { refreshAll(app); } catch (Throwable ignored) { }
                    pr.finish();
                }
            }).start();
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleNext(context.getApplicationContext());
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(tickIntent(context));
        } catch (Throwable ignored) { }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            final Context app = context.getApplicationContext();
            if (ACTION_TICK.equals(action)) {
                android.content.SharedPreferences sp = app.getSharedPreferences(Core.PREFS, 0);
                sp.edit().putInt("tick_count", sp.getInt("tick_count", 0) + 1).apply();
                mark(app, "零点闹钟");
            } else {
                mark(app, "日期/时区广播");
            }
            refreshAll(app);
            scheduleNext(app);
        }
    }

    static void mark(Context context, String src) {
        context.getSharedPreferences(Core.PREFS, 0).edit()
                .putLong("last_refresh", System.currentTimeMillis())
                .putString("last_src", src).apply();
    }

    /** 手动模拟一次零点触发，返回当前桌面上的小组件数量 */
    static int simulateTick(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        Intent i = new Intent(context, WidgetProvider.class);
        i.setAction(ACTION_TICK);
        context.sendBroadcast(i);
        return ids.length;
    }

    static String diagText(Context context) {
        android.content.SharedPreferences sp = context.getSharedPreferences(Core.PREFS, 0);
        long last = sp.getLong("last_refresh", 0);
        String src = sp.getString("last_src", "—");
        int cnt = sp.getInt("tick_count", 0);
        long next = sp.getLong("next_alarm", 0);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("下次自动刷新：").append(next > 0 ? f.format(new java.util.Date(next)) : "未登记");
        if (next > 0) {
            long left = next - System.currentTimeMillis();
            long h = left / 3600000L, m = (left % 3600000L) / 60000L;
            sb.append("（还有 ").append(h).append(" 小时 ").append(m).append(" 分）");
        }
        sb.append("\n最近一次刷新：").append(last > 0 ? f.format(new java.util.Date(last)) + " · " + src : "—");
        sb.append("\n零点触发累计：").append(cnt).append(" 次");
        int n = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, WidgetProvider.class)).length;
        sb.append("\n当前桌面小组件：").append(n).append(" 个");
        return sb.toString();
    }

    static PendingIntent tickIntent(Context context) {
        Intent i = new Intent(context, WidgetProvider.class);
        i.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, ALARM_REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleNext(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = tickIntent(context);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 20);
        cal.set(Calendar.MILLISECOND, 0);
        long at = cal.getTimeInMillis();
        context.getSharedPreferences(Core.PREFS, 0).edit().putLong("next_alarm", at).apply();
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable t) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi); } catch (Throwable ignored) { }
        }
    }

    public static void refreshAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        for (int id : ids) update(context, mgr, id);
    }

    static void update(Context context, AppWidgetManager mgr, int id) {
        int style = Core.style(context);
        boolean classic = Core.classic(context);
        int layout;
        if (style == 1) layout = classic ? R.layout.widget_style_pink_classic : R.layout.widget_style_pink;
        else if (style == 2) layout = classic ? R.layout.widget_style_dots_classic : R.layout.widget_style_dots;
        else if (style == 3) layout = classic ? R.layout.widget_style_night_classic : R.layout.widget_style_night;
        else layout = classic ? R.layout.widget_style_paper_classic : R.layout.widget_style_paper;
        RemoteViews rv = new RemoteViews(context.getPackageName(), layout);

        int days = Core.daysTogether(context);
        rv.setTextViewText(R.id.w_days, days > 0 ? Core.comma(days) : "—");
        rv.setTextViewText(R.id.w_span, days > 0 ? Core.spanText(context) : "还没有到约好的那一天");
        rv.setTextViewText(R.id.w_term, Core.currentTerm() + " " + Core.termIndex() + "/24");

        List<String[]> items = Core.items(context);
        if (items.size() > 0) {
            String[] a = items.get(0);
            String left = a[1];
            String num = left;
            if ("明天".equals(left)) num = "1 天";
            boolean today = "今天".equals(left);
            rv.setViewVisibility(R.id.w_next_pre, today ? View.GONE : View.VISIBLE);
            rv.setTextViewText(R.id.w_next, a[0]);
            rv.setTextViewText(R.id.w_next_num, num);
        } else {
            rv.setViewVisibility(R.id.w_next_pre, View.GONE);
            rv.setTextViewText(R.id.w_next, "还没有要过的日子");
            rv.setTextViewText(R.id.w_next_num, "");
        }

        int hc = style == 1 ? 0xFFE0708F : (style == 3 ? 0xFFE9A0B4 : 0xFFDE8AA0);
        android.graphics.Bitmap hb = Art.hearts(Art.dp(context, 22), Art.dp(context, 9), hc);
        rv.setImageViewBitmap(R.id.w_heart_l, hb);
        rv.setImageViewBitmap(R.id.w_heart_r, hb);

        // 后面的日子做成小方框，按实际宽度能放几个放几个
        int[] chipIds = {R.id.w_chip1, R.id.w_chip2, R.id.w_chip3, R.id.w_chip4};
        for (int cid : chipIds) rv.setViewVisibility(cid, View.GONE);

        int availDp = 300;
        try {
            Bundle o = mgr.getAppWidgetOptions(id);
            int wdp = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
            if (wdp > 0) availDp = wdp;
        } catch (Throwable ignored) { }

        Paint pt = new Paint();
        pt.setTextSize(8f * context.getResources().getDisplayMetrics().scaledDensity);
        float availPx = (availDp - 34) * context.getResources().getDisplayMetrics().density;
        float used = 0;
        int shown = 0;
        for (int i = 1; i < items.size() && shown < chipIds.length; i++) {
            String[] b = items.get(i);
            String label = b[0] + " " + b[1];
            float need = pt.measureText(label) + Art.dp(context, 12) + Art.dp(context, 5);
            if (shown > 0 && used + need > availPx) break;
            used += need;
            rv.setViewVisibility(chipIds[shown], View.VISIBLE);
            rv.setTextViewText(chipIds[shown], label);
            shown++;
        }

        rv.setTextViewText(R.id.w_quote, Llm.todayQuote(context));

        int idx = Core.termIndex();
        int density = (int) context.getResources().getDisplayMetrics().density;
        int wDp = 300, hDp = 124;
        try {
            Bundle o = mgr.getAppWidgetOptions(id);
            if (o != null) {
                int a = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int b = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                if (a > 0) wDp = a;
                if (b > 0) hDp = b;
            }
        } catch (Throwable ignored) { }

        try {
            // 背景插画：降到 0.45 倍分辨率，省内存也够清晰
            int bw = Math.max(160, (int) (wDp * density * 0.45f));
            int bh = Math.max(80, (int) (hDp * density * 0.45f));
            rv.setImageViewBitmap(R.id.w_bg, Art.background(style, bw, bh));

            // 右上角天气小牌：整块画成位图，外框随天气变化
            int wkind = Weather.kind(context);
            if (wkind < 0) wkind = 1;
            String sp2 = Weather.shortPlace(context);
            String wdesc = (sp2.isEmpty() ? "" : sp2 + " · ") + Weather.desc(context);
            int stroke = style == 3 ? 0xFFC8A46A : (style == 1 ? 0xFFC9788C : 0xFFA5762F);
            int tcol   = style == 3 ? 0xFFF2E9DA : (style == 1 ? 0xFF4A2732 : 0xFF232019);
            int scol   = style == 3 ? 0xFFB7AC99 : (style == 1 ? 0xFF8A4A5C : 0xFF8D8478);
            rv.setImageViewBitmap(R.id.w_wpanel, Art.weatherPanel(wkind, Weather.temp(context) + "°",
                    wdesc, Art.dp(context, 84), Art.dp(context, 52), stroke, tcol, scol));
            // 中间相框：按卡片实际宽度算，照片居中裁切铺满
            android.graphics.Bitmap ph = Photo.load(context, 900);
            int cardDp = 300;
            try {
                android.os.Bundle o = mgr.getAppWidgetOptions(id);
                if (o != null) cardDp = Math.max(240, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300));
            } catch (Throwable ignored) { }
            // 三个环分散在卡片上边沿的左中右
            float dens = context.getResources().getDisplayMetrics().density;
            rv.setImageViewBitmap(R.id.w_rings,
                    Art.ringsSpread((int) (dens * cardDp), Art.dp(context, 22), dens, stroke));
            int fwDp = Math.max(60, Math.min(120, cardDp - 210));
            int fbg = style == 3 ? 0xFF2A2620 : (style == 1 ? 0xFFFDEFF3 : 0xFFFBF6EC);
            if (!classic) {
                rv.setImageViewBitmap(R.id.w_photo, Art.photoFrame(ph, Art.dp(context, fwDp), Art.dp(context, 66), stroke, fbg, tcol));
            }

            if (style == 2) {
                int w = Math.max(Art.dp(context, 200), context.getResources().getDisplayMetrics().widthPixels - Art.dp(context, 96));
                rv.setImageViewBitmap(R.id.w_art, Art.dots(w, Art.dp(context, 3), idx, 0xFFA5762F, 0xE6D8C2));
            }
        } catch (Throwable ignored) { }

        try { Weather.refreshIfStale(context.getApplicationContext()); } catch (Throwable ignored) { }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_root, pi);

        mgr.updateAppWidget(id, rv);
    }
}
