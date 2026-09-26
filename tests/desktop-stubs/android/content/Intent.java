package android.content;
public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_CLEAR_TOP=2;
    public String action; public int flags;
    public Intent(Context context,Class<?> type){}
    public Intent setAction(String value){action=value;return this;}
    public Intent addFlags(int value){flags|=value;return this;}
}
