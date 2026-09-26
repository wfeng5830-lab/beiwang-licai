package android.content;
public abstract class Context {
    public static final int MODE_PRIVATE=0;
    public abstract SharedPreferences getSharedPreferences(String name,int mode);
    public Context getApplicationContext(){return this;}
    public <T> T getSystemService(Class<T> type){return null;}
}
