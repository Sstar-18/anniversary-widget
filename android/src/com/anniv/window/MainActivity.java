package com.anniv.window;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 设置页：起始日 + 生日/纪念日列表 + 每日一句 API + 悬浮小窗开关。 */
public class MainActivity extends Activity {

    private int y = 2023, m = 5, d = 20;
    private TextView dateBtn, tvQuote;
    private LinearLayout listExtra, listStyle;
    private EditText etKey, etCity;
    private TextView tvPlace;
    private boolean guard = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        String s = Core.getStart(this);
        y = Integer.parseInt(s.substring(0, 4));
        m = Integer.parseInt(s.substring(5, 7));
        d = Integer.parseInt(s.substring(8, 10));

        dateBtn = (TextView) findViewById(R.id.dateBtn);
        listExtra = (LinearLayout) findViewById(R.id.listExtra);
        etKey = (EditText) findViewById(R.id.etKey);
        etCity = (EditText) findViewById(R.id.etCity);
        tvPlace = (TextView) findViewById(R.id.tvPlace);
        tvQuote = (TextView) findViewById(R.id.tvQuote);

        paintDate();
        dateBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new DatePickerDialog(MainActivity.this, (view, yy, mm, dd) -> {
                    y = yy; m = mm + 1; d = dd;
                    paintDate();
                }, y, m - 1, d).show();
            }
        });

        listStyle = (LinearLayout) findViewById(R.id.listStyle);
        buildStyles();

        for (String[] e : Core.extras(this)) {
            int mm = Integer.parseInt(e[1].substring(0, 2));
            int dd = Integer.parseInt(e[1].substring(3, 5));
            addRow(e[0], mm, dd);
        }

        findViewById(R.id.btnAddExtra).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { addRow("", 0, 0); }
        });

        etKey.setText(Llm.key(this));
        etCity.setText(Weather.city(this));
        paintPause();
        ((TextView) findViewById(R.id.btnPause)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean now = !Core.paused(MainActivity.this);
                Core.setPaused(MainActivity.this, now);
                paintPause();
                WidgetProvider.refreshAll(MainActivity.this);
                Toast.makeText(MainActivity.this, now
                        ? "已暂停：天数停在 " + Core.daysTogether(MainActivity.this) + " 天"
                        : "已恢复：接着第 " + Core.daysTogether(MainActivity.this) + " 天走", Toast.LENGTH_SHORT).show();
            }
        });

        paintPlace();
        tvQuote.setText("今日 · " + Llm.todayQuote(this));

        final TextView btnSrc = (TextView) findViewById(R.id.btnSrc);
        btnSrc.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int next = Weather.source(MainActivity.this) == 0 ? 1 : 0;
                Weather.setSource(MainActivity.this, next);
                paintSrc();
                Weather.force(MainActivity.this);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        paintPlace();
                        WidgetProvider.refreshAll(MainActivity.this);
                    }
                }, 3500);
            }
        });
        paintSrc();

        final TextView btnWeather = (TextView) findViewById(R.id.btnWeather);
        btnWeather.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                btnWeather.setText("正在刷新…");
                Weather.force(MainActivity.this);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        btnWeather.setText("刷新天气");
                        paintPlace();
                        WidgetProvider.refreshAll(MainActivity.this);
                        Toast.makeText(MainActivity.this, "天气已刷新：" + Weather.temp(MainActivity.this) + "° " + Weather.desc(MainActivity.this), Toast.LENGTH_SHORT).show();
                    }
                }, 3500);
            }
        });

        findViewById(R.id.btnLoc).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 21);
                } else {
                    locateNow();
                }
            }
        });

        findViewById(R.id.btnGen).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String k = etKey.getText().toString().trim();
                if (k.length() < 10) {
                    Toast.makeText(MainActivity.this, "先填上智谱的 API Key", Toast.LENGTH_SHORT).show();
                    return;
                }
                Llm.setKey(MainActivity.this, k);
                Toast.makeText(MainActivity.this, "正在生成…", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    public void run() {
                        final String st = Llm.fetch(MainActivity.this);
                        runOnUiThread(() -> {
                            WidgetProvider.refreshAll(MainActivity.this);
                            if ("ok".equals(st)) {
                                tvQuote.setText("今日 · " + Llm.todayQuote(MainActivity.this));
                                Toast.makeText(MainActivity.this, "已生成，桌面已更新", Toast.LENGTH_SHORT).show();
                            } else {
                                tvQuote.setText("没生成成功：" + st);
                                Toast.makeText(MainActivity.this, st, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }).start();
            }
        });

        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });

        paintPhoto();
        findViewById(R.id.btnPhoto).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.setType("image/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(pick, "选一张照片"), 31);
                } catch (Throwable e) {
                    Toast.makeText(MainActivity.this, "这台手机没有相册可选", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.btnPhotoClear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Photo.clear(MainActivity.this);
                paintPhoto();
                WidgetProvider.refreshAll(MainActivity.this);
                Toast.makeText(MainActivity.this, "已清掉相框里的照片", Toast.LENGTH_SHORT).show();
            }
        });

        paintLayout();
        findViewById(R.id.btnLayout).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean nowClassic = !Core.classic(MainActivity.this);
                Core.setClassic(MainActivity.this, nowClassic);
                paintLayout();
                WidgetProvider.refreshAll(MainActivity.this);
                Toast.makeText(MainActivity.this,
                        nowClassic ? "已回退到无相框版" : "已切回相框版", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void paintLayout() {
        boolean classic = Core.classic(this);
        ((TextView) findViewById(R.id.tvLayout)).setText(classic
                ? "现在：经典版（无相框，字排得开）"
                : "现在：相框版（中间放照片）");
        ((TextView) findViewById(R.id.btnLayout)).setText(classic
                ? "切回相框版"
                : "回退到无相框版");
    }

    private static final String[] STYLE_NAMES = {"纸笺", "樱粉", "点阵", "夜读"};
    private static final String[] STYLE_DESCS = {
            "米白宣纸，宋体大数字",
            "樱粉渐变，小爱心和星光",
            "带二十四节气进度条",
            "墨黑夜色，香槟金"
    };

    private void buildStyles() {
        listStyle.removeAllViews();
        int cur = Core.style(this);
        for (int i = 0; i < STYLE_NAMES.length; i++) {
            final int idx = i;
            View row = getLayoutInflater().inflate(R.layout.row_style, listStyle, false);
            ((TextView) row.findViewById(R.id.stName)).setText(STYLE_NAMES[i]);
            ((TextView) row.findViewById(R.id.stDesc)).setText(STYLE_DESCS[i]);
            TextView mark = (TextView) row.findViewById(R.id.stMark);
            mark.setText(i == cur ? "●" : "○");
            mark.setTextColor(i == cur ? 0xFFA9443C : 0xFFB3A995);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Core.setStyle(MainActivity.this, idx);
                    buildStyles();
                    WidgetProvider.refreshAll(MainActivity.this);
                    Toast.makeText(MainActivity.this, "已换成「" + STYLE_NAMES[idx] + "」", Toast.LENGTH_SHORT).show();
                }
            });
            listStyle.addView(row);
        }
    }

    private void addRow(String name, int mm, int dd) {
        View row = getLayoutInflater().inflate(R.layout.row_extra, listExtra, false);
        EditText nm = (EditText) row.findViewById(R.id.exName);
        final TextView dt = (TextView) row.findViewById(R.id.exDate);
        nm.setText(name);

        final int[] md = new int[]{mm, dd};
        row.setTag(md);
        dt.setText(md[0] > 0 ? String.format(Locale.CHINA, "%02d-%02d", md[0], md[1]) : "选择日期");

        dt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new DatePickerDialog(MainActivity.this, (view, yy, mo, day) -> {
                    md[0] = mo + 1;
                    md[1] = day;
                    dt.setText(String.format(Locale.CHINA, "%02d-%02d", md[0], md[1]));
                }, 2000, (md[0] > 0 ? md[0] : 2) - 1, md[1] > 0 ? md[1] : 14).show();
            }
        });

        row.findViewById(R.id.exDel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listExtra.removeView(row); }
        });

        listExtra.addView(row);
    }

    private void save() {
        Core.setStart(this, y, m, d);

        List<String[]> list = new ArrayList<String[]>();
        for (int i = 0; i < listExtra.getChildCount(); i++) {
            View row = listExtra.getChildAt(i);
            int[] md = (int[]) row.getTag();
            if (md == null || md[0] <= 0) continue;
            String name = ((EditText) row.findViewById(R.id.exName)).getText().toString().trim();
            if (name.length() == 0) name = "纪念日";
            list.add(new String[]{name, String.format(Locale.CHINA, "%02d-%02d", md[0], md[1])});
        }
        Core.setExtras(this, list);

        String oldKey = Llm.key(this);
        String newKey = etKey.getText().toString().trim();
        if (oldKey.equals(newKey)) {
            Llm.setKey(this, newKey);
        } else {
            Llm.setKey(this, newKey);
            getSharedPreferences(Core.PREFS, 0).edit()
                    .remove("quoteDate").remove("quoteText").apply();
        }

        String savedCity = Weather.city(this);
        String newCity = etCity.getText().toString().trim();
        if (newCity.isEmpty()) newCity = "青岛";
        if (!newCity.equals(savedCity)) Weather.manualCity(this, newCity);   // 改过城市名就切回按名字查
        Weather.refreshIfStale(getApplicationContext());

        WidgetProvider.refreshAll(this);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void paintDate() {
        dateBtn.setText(String.format(Locale.CHINA, "%04d 年 %02d 月 %02d 日", y, m, d));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 进 App 时顺手刷一次小组件：跨过 5 小时时段就换新句子
        WidgetProvider.refreshAll(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 21 && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            locateNow();
        } else if (code == 21) {
            Toast.makeText(this, "没给定位权限，可以手动填城市", Toast.LENGTH_SHORT).show();
        }
    }

    /** 取最近一次定位坐标，按坐标取天气（不用再猜地名）。 */
    private void locateNow() {
        Location best = null;
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            String[] ps = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            for (String p : ps) {
                if (!lm.isProviderEnabled(p)) continue;
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (Throwable ignored) { }
        if (best == null) {
            Toast.makeText(this, "还没取到定位，出门或打开定位开关后再试一次", Toast.LENGTH_SHORT).show();
            return;
        }
        final double lat = best.getLatitude(), lon = best.getLongitude();
        Toast.makeText(this, "正在按你的位置取天气…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                final String[] rg = Weather.reverseGeocode(lat, lon);
                final String place = rg[0];
                runOnUiThread(new Runnable() {
                    public void run() {
                        Weather.setFix(MainActivity.this, lat, lon, place, rg[1]);
                        paintPlace();
                        Weather.refreshIfStale(getApplicationContext());
                        WidgetProvider.refreshAll(MainActivity.this);
                        Toast.makeText(MainActivity.this, place.isEmpty() ? "已按定位更新" : place, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void paintPause() {
        boolean p = Core.paused(this);
        TextView t = (TextView) findViewById(R.id.tvPause);
        TextView b = (TextView) findViewById(R.id.btnPause);
        if (p) {
            t.setText("已暂停：天数停在 " + Core.daysTogether(this) + " 天（" + Core.spanText(this)
                    + "），只停这一个数，已停 " + Core.pausedDays(this) + " 天。恢复后按真实日期继续算。");
            b.setText("恢复计时（接着在一起）");
        } else {
            t.setText("正在计时：第 " + Core.daysTogether(this) + " 天，已走过 " + Core.spanText(this)
                    + "。点下面的按钮，只把「在一起」这个天数停住，别的都不动。");
            b.setText("暂停计时");
        }
    }

    private void paintSrc() {
        TextView b = (TextView) findViewById(R.id.btnSrc);
        if (b != null) b.setText(Weather.source(this) == 0
                ? "数据源：国内（中国天气网）· 点我切换"
                : "数据源：国际（Open-Meteo）· 点我切换");
    }

    private void paintPlace() {
        String p = Weather.place(this);
        boolean loc = Weather.locationMode(this);
        if (p.isEmpty()) {
            tvPlace.setText(loc ? "天气地点：已按定位（正在获取地名）" : "天气地点：未设置，默认按城市名查");
        } else {
            tvPlace.setText("天气地点：" + p + (loc ? "（手机定位）" : "（按城市名）"));
        }
        TextView w = (TextView) findViewById(R.id.tvWeatherNow);
        if (w != null) {
            w.setText("现在 " + Weather.temp(this) + "° " + Weather.desc(this) + " · " + Weather.stamp(this));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 31 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Photo.save(this, data.getData());
            paintPhoto();
            WidgetProvider.refreshAll(this);
            Toast.makeText(this, "照片放好啦，桌面已更新", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "这张照片存不下来：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void paintPhoto() {
        TextView tv = (TextView) findViewById(R.id.tvPhoto);
        if (tv == null) return;
        tv.setText(Photo.has(this)
                ? "已放好一张，桌面中间就是它；换一张直接再选。"
                : "还没放照片，中间那块会显示一个空相框。");
    }

}
