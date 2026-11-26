package com.anniv.window;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 相框里那张照片：存成应用私有目录里的一个 jpg，小组件直接读 */
public class Photo {

    private static File file(Context c) {
        return new File(c.getFilesDir(), "photo.jpg");
    }

    public static boolean has(Context c) {
        return file(c).exists();
    }

    /** 从相册选中的图片缩到长边 1400 再存，省内存也省流量 */
    public static void save(Context c, Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 1400) sample *= 2;

        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        in = c.getContentResolver().openInputStream(uri);
        Bitmap bm = BitmapFactory.decodeStream(in, null, o2);
        in.close();
        if (bm == null) throw new Exception("这张图读不出来");

        FileOutputStream fo = new FileOutputStream(file(c));
        bm.compress(Bitmap.CompressFormat.JPEG, 88, fo);
        fo.flush();
        fo.close();
        bm.recycle();
    }

    public static void clear(Context c) {
        File f = file(c);
        if (f.exists()) f.delete();
    }

    /** 读一张缩小后的照片给小组件用 */
    public static Bitmap load(Context c, int maxSide) {
        File f = file(c);
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }
}
