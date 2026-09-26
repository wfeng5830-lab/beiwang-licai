package android.app;
import android.content.*;
public class PendingIntent {
    public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;
    public static PendingIntent getBroadcast(Context context,int code,Intent intent,int flags){return new PendingIntent();}
    public Object getIntentSender(){return this;}
}
