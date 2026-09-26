package android.content.pm;
import android.content.*;
import android.graphics.drawable.Icon;
public class ShortcutInfo {
    public String id; public Icon icon; public Intent intent;
    public String getId(){return id;}
    public static class Builder {
        private final ShortcutInfo shortcut=new ShortcutInfo();
        public Builder(Context context,String id){shortcut.id=id;}
        public Builder setShortLabel(String label){return this;}
        public Builder setLongLabel(String label){return this;}
        public Builder setActivity(ComponentName activity){return this;}
        public Builder setIntent(Intent intent){shortcut.intent=intent;return this;}
        public Builder setIcon(Icon icon){shortcut.icon=icon;return this;}
        public ShortcutInfo build(){return shortcut;}
    }
}
