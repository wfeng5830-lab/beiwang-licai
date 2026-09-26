package cn.dailyledger.app;
import android.content.*;
import android.content.pm.ShortcutManager;
import org.json.*;
import java.util.*;

public final class DesktopShortcutTest {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static class Prefs implements SharedPreferences {
        final Map<String,String> values=new HashMap<>();
        public String getString(String k,String d){return values.getOrDefault(k,d);}
        public Editor edit(){return new Editor(){
            final Map<String,String> edits=new HashMap<>();
            public Editor putString(String k,String v){edits.put(k,v);return this;}
            public Editor remove(String k){edits.put(k,null);return this;}
            public boolean commit(){edits.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});return true;}
        };}
    }
    static class Memory extends Context {
        final Map<String,Prefs> stores=new HashMap<>();final ShortcutManager manager=new ShortcutManager();
        public SharedPreferences getSharedPreferences(String n,int mode){return stores.computeIfAbsent(n,k->new Prefs());}
        public <T> T getSystemService(Class<T> type){return type.cast(manager);}
    }
    static JSONObject memo(String id,int slot)throws Exception{return new JSONObject().put("id",id).put("title","").put("body","事项").put("slot",slot);}
    public static void main(String[] args)throws Exception{
        Memory ctx=new Memory();ShortcutManager m=ctx.manager;LedgerStore ledger=new LedgerStore(ctx);
        m.supported=false;DesktopShortcut.requestPin(ctx);
        check(m.pinCalls==0,"unsupported desktop does not request pin");
        m.supported=true;DesktopShortcut.requestPin(ctx);
        check(m.pinCalls==1&&!new JSONObject(DesktopShortcut.status(ctx)).getBoolean("pinned"),"accepted request is not reported as confirmed pin");
        check(m.requested.intent.action.equals(DesktopShortcut.ACTION_OPEN),"shortcut uses dedicated home-entry action");
        ledger.mutate("saveMemo",memo("a",0).toString());
        m.pinned.add(m.requested);DesktopShortcut.refresh(ctx,true);
        check(m.updateCalls==1&&m.updated.icon.value.toString().contains("事"),"pin callback renders latest data saved during confirmation");
        String id=m.requested.getId();DesktopShortcut.requestPin(ctx);
        check(m.pinCalls==1&&m.updated.getId().equals(id),"already pinned updates stable ID without duplicating icon");
        int updates=m.updateCalls;DesktopShortcut.refresh(ctx,false);
        check(m.updateCalls==updates,"unchanged projection avoids repeated update API calls");
        ledger.mutate("moveMemo",new JSONObject().put("id","a").put("slot",9).toString());
        m.acceptUpdate=false;DesktopShortcut.refresh(ctx,false);
        check(new JSONObject(DesktopShortcut.status(ctx)).getString("message").contains("暂缓"),"rate limit is reported");
        updates=m.updateCalls;m.acceptUpdate=true;DesktopShortcut.refresh(ctx,false);
        check(m.updateCalls==updates+1,"rate-limited update retried instead of cached as success");
        ledger.mutate("saveMemo",memo("a",9).put("done",true).toString());
        m.throwUpdate=true;DesktopShortcut.refresh(ctx,false);
        check(new JSONObject(ledger.read()).getJSONArray("memos").getJSONObject(0).getBoolean("done"),"desktop failure never undoes stored completion");
        m.throwUpdate=false;DesktopShortcut.refresh(ctx,false);
        check(m.updated.icon.value.equals("[[0],[0],[0],[0]]"),"retry clears all quadrants after completion");
        m.pinned.clear();updates=m.updateCalls;DesktopShortcut.refresh(ctx,false);
        check(m.updateCalls==updates&&!new JSONObject(DesktopShortcut.status(ctx)).getBoolean("pinned"),"removed shortcut not recreated without user action");
        DesktopShortcut.requestPin(ctx);check(m.pinCalls==2&&m.requested.getId().equals(id),"re-add uses same stable identifier");
        m.acceptRequest=false;check(DesktopShortcut.requestPin(ctx).contains("未接受"),"rejected pin request has accurate message");
        System.out.println("DesktopShortcutTest: "+checks+" checks passed");
    }
}
