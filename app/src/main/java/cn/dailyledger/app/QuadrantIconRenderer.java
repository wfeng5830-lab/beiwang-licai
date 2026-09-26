package cn.dailyledger.app;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

final class QuadrantIconRenderer {
    private static final int[] COLORS = {0xfff6d9d3, 0xffd3e9df, 0xfff5e7c5, 0xffdce4f0};
    private QuadrantIconRenderer() {}
    // minSdk 26 natively supports this bundled vector; no AppCompat dependency is needed.
    @SuppressLint("UseCompatLoadingForDrawables")
    static Bitmap draw(Context context, QuadrantIconModel model) {
        final int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (model.isEmpty()) {
            Drawable original = context.getDrawable(R.drawable.ic_launcher);
            if (original != null) { original.setBounds(0, 0, size, size); original.draw(canvas); }
            return bitmap;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, size, size), 44, 44, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawColor(0xfff9f8f2);
        for (int q = 0; q < 4; q++) {
            float left = 12 + (q % 2) * 87, top = 12 + (q / 2) * 87;
            RectF tile = new RectF(left, top, left + 81, top + 81);
            paint.setColor(model.count(q) == 0 ? 0xffedf2ef : COLORS[q]);
            canvas.drawRoundRect(tile, 6, 6, paint);
            if (model.count(q) == 1) {
                letter(canvas, paint, model.mark(q, 0), tile, 59);
            } else if (model.count(q) > 1) {
                for (int i = 0; i < model.visibleCount(q); i++) {
                    float x = left + 3 + (i % 2) * 39, y = top + 3 + (i / 2) * 39;
                    RectF cell = new RectF(x, y, x + 36, y + 36);
                    paint.setColor(0x50ffffff);canvas.drawRoundRect(cell, 3, 3, paint);
                    letter(canvas, paint, model.mark(q, i), cell, 30);
                }
                if (model.overflow(q) > 0) {
                    RectF badge = new RectF(tile.right - 27, tile.bottom - 19, tile.right + 2, tile.bottom + 2);
                    paint.setColor(0xfff9f8f2);canvas.drawRoundRect(badge, 5, 5, paint);
                    letter(canvas, paint, "+" + model.overflow(q), badge, 18);
                }
            }
        }
        return bitmap;
    }
    private static void letter(Canvas canvas, Paint paint, String value, RectF box, float size) {
        paint.setColor(Color.rgb(23, 51, 43));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(size);
        float width = paint.measureText(value);
        if (width > box.width() - 3) paint.setTextSize(size * (box.width() - 3) / width);
        Rect bounds = new Rect();paint.getTextBounds(value, 0, value.length(), bounds);
        canvas.drawText(value, box.centerX() - paint.measureText(value) / 2,
                box.centerY() - (bounds.top + bounds.bottom) / 2f, paint);
    }
}
