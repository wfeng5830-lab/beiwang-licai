package cn.dailyledger.app;
import android.content.*;
import org.json.*;
import java.util.*;
public final class LedgerStoreTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Memory extends Context implements SharedPreferences {
        final Map<String,String> data=new HashMap<>();
        public SharedPreferences getSharedPreferences(String n,int m){return this;}
        public String getString(String k,String d){return data.getOrDefault(k,d);}
        public Editor edit(){return new Editor(){String k,v;public Editor putString(String key,String value){k=key;v=value;return this;}public boolean commit(){data.put(k,v);return true;}};}
    }
    static JSONObject entry(String id)throws Exception{return new JSONObject().put("id",id).put("cents",345).put("date","2026-09-06").put("time","18:07").put("channel","wechat").put("category","餐饮").put("note","旧的手动记录").put("source","manual");}
    static JSONObject call(LedgerStore s,String action,Object payload)throws Exception{return new JSONObject(s.mutate(action,payload.toString()));}
    static JSONObject state(LedgerStore s)throws Exception{return new JSONObject(s.read());}
    public static void main(String[] args)throws Exception{
        Memory m=new Memory();JSONObject old=new JSONObject().put("entries",new JSONArray().put(entry("old"))).put("pending",new JSONArray().put(new JSONObject().put("id","old_notice"))).put("seen",new JSONArray().put("old_notice"));m.data.put("state",old.toString());LedgerStore s=new LedgerStore(m);
        check(state(s).getJSONArray("entries").length()==1,"upgrade keeps booked record");check(state(s).getJSONArray("pending").length()==0,"upgrade clears obsolete notification candidates");
        BillParser.Bill bill=new BillParser.Bill("2026-09-06","18:07","测试超市","wechat",345);
        JSONObject scan=s.scan(Arrays.asList(bill));check(scan.getInt("added")==1&&scan.getInt("conflicts")==1,"manual collision queued for review");
        check(s.scan(Arrays.asList(bill)).getInt("duplicates")==1,"overlap duplicate pending");JSONObject candidate=state(s).getJSONArray("pending").getJSONObject(0);
        String key=candidate.getString("sourceKey");candidate.put("category","购物");check(call(s,"editCandidate",new JSONObject().put("id",candidate.getString("id")).put("entry",candidate)).getBoolean("ok"),"edit pending accepted");check(state(s).getJSONArray("entries").length()==1,"editing does not book transaction");candidate=state(s).getJSONArray("pending").getJSONObject(0);check(candidate.getString("sourceKey").equals(key)&&candidate.getString("category").equals("购物"),"edit keeps scan identity and category");
        check(!call(s,"confirm",new JSONObject().put("id",candidate.getString("id")).put("entry",candidate)).getBoolean("ok"),"collision requires explicit confirmation");
        check(call(s,"confirm",new JSONObject().put("id",candidate.getString("id")).put("entry",candidate).put("forceDuplicate",true)).getBoolean("ok"),"explicit distinct transaction accepted");
        check(s.scan(Arrays.asList(bill)).getInt("duplicates")==1,"already booked scan skipped");check(state(s).getJSONArray("entries").length()==2,"exactly two intentional entries");
        JSONObject edited=state(s).getJSONArray("entries").getJSONObject(1);edited.put("note","修改后的备注");call(s,"upsert",edited);check(s.scan(Arrays.asList(bill)).getInt("duplicates")==1,"editing retains observed source identity");
        BillParser.Bill second=new BillParser.Bill("2026-09-07","12:00","便利店","alipay",1200);s.scan(Arrays.asList(second));candidate=state(s).getJSONArray("pending").getJSONObject(0);call(s,"dismiss",new JSONObject().put("id",candidate.getString("id")));check(s.scan(Arrays.asList(second)).getInt("duplicates")==1,"ignored candidate not resurrected");
        BillParser.Bill a=new BillParser.Bill("2026-09-08","13:00","店铺甲","wechat",1500),b=new BillParser.Bill("2026-09-08","13:00","店铺乙","wechat",1500);s.scan(Arrays.asList(a,b));JSONArray pending=state(s).getJSONArray("pending"),ids=new JSONArray();for(int i=0;i<pending.length();i++)ids.put(pending.getJSONObject(i).getString("id"));JSONObject batch=call(s,"confirmBatch",new JSONObject().put("ids",ids));check(batch.getInt("confirmed")==1&&batch.getInt("conflicts")==1,"batch rechecks collisions within batch");
        JSONArray before=state(s).getJSONArray("entries");JSONObject copy=new JSONObject(before.getJSONObject(1).toString()).put("id","different_import_id");call(s,"import",new JSONArray().put(copy));check(state(s).getJSONArray("entries").length()==before.length(),"backup dedup by source identity");
        int length=state(s).getJSONArray("entries").length();check(!call(s,"import",new JSONArray().put(entry("new_valid")).put(entry("bad").put("cents",-1))).getBoolean("ok"),"invalid import rejected");check(state(s).getJSONArray("entries").length()==length,"failed import atomic");
        JSONObject memo=new JSONObject().put("id","m_test").put("title","购物清单").put("body","牛奶\n面包");
        check(call(s,"saveMemo",memo).getBoolean("ok"),"memo create");
        s=new LedgerStore(m);check(state(s).getJSONArray("memos").getJSONObject(0).getString("body").equals("牛奶\n面包"),"memo survives reopening store");
        memo.put("body","牛奶\n面包\n鸡蛋");call(s,"saveMemo",memo);check(state(s).getJSONArray("memos").length()==1,"memo edit replaces existing");
        check(state(s).getJSONArray("entries").length()==length,"memo editing preserves expenses");
        JSONObject backup=new JSONObject(s.exportBackup());LedgerStore restored=new LedgerStore(new Memory());
        check(call(restored,"importBundle",backup).getBoolean("ok"),"combined backup imports");call(restored,"importBundle",backup);
        check(state(restored).getJSONArray("memos").length()==1&&state(restored).getJSONArray("memos").getJSONObject(0).getString("body").contains("鸡蛋"),"memo backup preserved and deduplicated");
        JSONObject invalid=new JSONObject().put("entries",new JSONArray().put(entry("rollback_entry"))).put("memos",new JSONArray().put(new JSONObject().put("id","bad").put("title","").put("body"," ")));
        check(!call(restored,"importBundle",invalid).getBoolean("ok"),"invalid memo prevents entire import");
        check(state(restored).getJSONArray("entries").length()==length,"combined import atomic");
        check(call(s,"deleteMemo",new JSONObject().put("id","m_test")).getBoolean("ok")&&state(new LedgerStore(m)).getJSONArray("memos").length()==0,"memo deletion persists");
        System.out.println("Ledger storage: "+checks+" checks passed");
    }
}
