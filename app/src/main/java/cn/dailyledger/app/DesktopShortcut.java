package cn.dailyledger.app;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import org.json.JSONObject;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single stable pinned shortcut; the installed application's launcher icon stays intact. */
final class DesktopShortcut {
    static final String ACTION_OPEN = "cn.dailyledger.app.OPEN_QUADRANTS_HOME";
    private static final String ID = "quadrants_home";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "desktop-shortcut");thread.setDaemon(true);return thread;
    });
    private DesktopShortcut() {}
    static void runAsync(Runnable task) { WORKER.execute(task); }
    static void refreshAsync(Context context, boolean force) {
        Context app = context.getApplicationContext();
        runAsync(() -> refresh(app,force));
    }

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("desktop_shortcut",Context.MODE_PRIVATE); }
    private static boolean pinned(ShortcutManager manager) {
        for (ShortcutInfo item : manager.getPinnedShortcuts()) if (ID.equals(item.getId())) return true;
        return false;
    }
    private static QuadrantIconModel model(Context context) throws Exception {
        return new QuadrantIconModel(new JSONObject(new LedgerStore(context).read()).optJSONArray("memos"));
    }
    private static ShortcutInfo shortcut(Context context, QuadrantIconModel model) {
        Intent entry = new Intent(context, MainActivity.class).setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new ShortcutInfo.Builder(context, ID).setShortLabel("日常账本")
                .setLongLabel("日常账本 · 四象限提醒")
                .setActivity(new ComponentName(context, MainActivity.class))
                .setIntent(entry).setIcon(Icon.createWithBitmap(QuadrantIconRenderer.draw(context,model))).build();
    }
    private static void message(Context context, String text) { prefs(context).edit().putString("message",text).apply(); }

    static synchronized String requestPin(Context context) {
        try {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null || !manager.isRequestPinShortcutSupported()) return "当前桌面不支持添加快捷图标";
            if (pinned(manager)) { refresh(context, true); return prefs(context).getString("message", "已提交图标更新"); }
            Intent callback = new Intent(context, ShortcutPinnedReceiver.class).setAction("cn.dailyledger.app.SHORTCUT_PINNED");
            PendingIntent result = PendingIntent.getBroadcast(context, 30, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean requested = manager.requestPinShortcut(shortcut(context, model(context)),result.getIntentSender());
            // A successful request only means the launcher accepted the request, not that the user confirmed.
            return requested ? "请在系统弹窗确认添加；添加后可将原图标从桌面移走" : "桌面未接受添加请求，请检查桌面设置";
        } catch (Exception error) { return "无法添加快捷图标，请稍后重试"; }
    }

    static synchronized void refresh(Context context, boolean force) {
        try {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null || !pinned(manager)) { prefs(context).edit().remove("fingerprint").remove("message").apply(); return; }
            QuadrantIconModel current = model(context);
            String fingerprint = "v1:" + current.fingerprint();
            if (!force && fingerprint.equals(prefs(context).getString("fingerprint", ""))) return;
            if (manager.updateShortcuts(Collections.singletonList(shortcut(context,current)))) {
                prefs(context).edit().putString("fingerprint",fingerprint)
                        .putString("message","已提交最新图标；显示速度由手机桌面决定").apply();
            } else {
                prefs(context).edit().remove("fingerprint").apply();
                message(context,"系统暂缓更新，下次打开软件将重试，也可点“刷新图标”");
            }
        } catch (Exception error) {
            // Shortcut failures never roll back a successfully saved ledger or memo.
            prefs(context).edit().remove("fingerprint").apply();
            message(context,"图标暂未更新，事项已保存；下次打开将重试");
        }
    }

    static synchronized String status(Context context) {
        try {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            boolean available = manager != null && manager.isRequestPinShortcutSupported();
            boolean isPinned = manager != null && pinned(manager);
            return new JSONObject().put("supported",available).put("pinned",isPinned)
                    .put("message",isPinned?prefs(context).getString("message","已添加桌面快捷图标"):"尚未添加桌面快捷图标").toString();
        } catch (Exception error) { return "{\"supported\":false,\"pinned\":false,\"message\":\"无法读取桌面状态，请稍后重试\"}"; }
    }
}
