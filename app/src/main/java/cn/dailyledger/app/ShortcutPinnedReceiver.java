package cn.dailyledger.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-render from current saved tasks if they changed while the pin confirmation was open. */
public final class ShortcutPinnedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult result=goAsync();
        Context app=context.getApplicationContext();
        DesktopShortcut.runAsync(()->{try{DesktopShortcut.refresh(app,true);}finally{result.finish();}});
    }
}
