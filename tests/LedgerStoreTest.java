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
        LedgerStore matrix=new LedgerStore(new Memory());
        for(int i=0;i<36;i++)check(call(matrix,"saveMemo",new JSONObject().put("id","q_"+i).put("title","").put("body","事项"+i).put("slot",i).put("mark","事")).getBoolean("ok"),"fill grid "+i);
        check(!call(matrix,"moveMemo",new JSONObject().put("id","q_0").put("slot",1)).getBoolean("ok"),"occupied slot rejects without overwriting");
        JSONObject completed=state(matrix).getJSONArray("memos").getJSONObject(1).put("done",true);call(matrix,"saveMemo",completed);
        check(state(matrix).getJSONArray("memos").getJSONObject(1).getInt("slot")==-1,"completion releases slot");
        check(call(matrix,"moveMemo",new JSONObject().put("id","q_0").put("slot",1)).getBoolean("ok"),"move into freed slot");
        check(!call(matrix,"moveMemo",new JSONObject().put("id","q_1").put("slot",0)).getBoolean("ok"),"completed task cannot occupy slot");
        check(!call(matrix,"moveMemo",new JSONObject().put("id","q_0").put("slot",36)).getBoolean("ok"),"slot range enforced");
        JSONObject mb=new JSONObject(matrix.exportBackup());LedgerStore roundtrip=new LedgerStore(new Memory());call(roundtrip,"importBundle",mb);
        check(state(roundtrip).getJSONArray("memos").getJSONObject(0).getInt("slot")==1&&state(roundtrip).getJSONArray("memos").getJSONObject(1).getBoolean("done"),"matrix and completed state survive backup");
        JSONObject conflict=new JSONObject().put("entries",new JSONArray()).put("memos",new JSONArray().put(new JSONObject().put("id","extra").put("title","").put("body","额外事项").put("slot",1)));
        check(call(roundtrip,"importBundle",conflict).getBoolean("ok")&&state(roundtrip).getJSONArray("memos").getJSONObject(36).getInt("slot")==-1,"backup slot conflict keeps incoming task unassigned");
        Memory dm=new Memory();LedgerStore dates=new LedgerStore(dm);
        BillParser.Bill undated=new BillParser.Bill("","12:30","测试日期店","alipay",1800,"09-30 12:30");
        JSONObject dr=dates.scan(Arrays.asList(undated));check(dr.getInt("added")==1&&dr.getInt("incomplete")==1,"incomplete scan is queued and reported");
        dates=new LedgerStore(dm);JSONObject dp=state(dates).getJSONArray("pending").getJSONObject(0);
        check(dp.getString("date").isEmpty()&&dp.getString("dateHint").contains("09-30"),"missing date and hint persist without fake date");
        check(state(dates).getJSONArray("entries").length()==0,"incomplete candidate not booked");
        JSONObject invented=new JSONObject(dp.toString()).put("date","2026-09-30");
        check(!call(dates,"confirm",new JSONObject().put("id",dp.getString("id")).put("entry",invented)).getBoolean("ok"),"confirm cannot bypass pending date completion");
        check(!call(dates,"upsert",dp).getBoolean("ok"),"booked entries still require real date");
        check(!call(dates,"import",new JSONArray().put(dp)).getBoolean("ok"),"imports still require real date");
        BillParser.Bill dated=new BillParser.Bill("2026-09-29","11:30","另一店铺","alipay",1200);dates.scan(Arrays.asList(dated));
        JSONArray dateIds=new JSONArray();for(int i=0;i<state(dates).getJSONArray("pending").length();i++)dateIds.put(state(dates).getJSONArray("pending").getJSONObject(i).getString("id"));
        JSONObject db=call(dates,"confirmBatch",new JSONObject().put("ids",dateIds));check(db.getInt("confirmed")==1&&db.getInt("incomplete")==1,"mixed batch confirms complete and leaves incomplete");
        check(state(dates).getJSONArray("pending").length()==1,"batch preserves missing-date review row");
        check(call(dates,"editCandidate",new JSONObject().put("id",dp.getString("id")).put("entry",invented)).getBoolean("ok"),"manual date completion saved");
        dp=state(dates).getJSONArray("pending").getJSONObject(0);
        String corrected=ScanIdentity.key("alipay","2026-09-30","12:30",1800,"测试日期店");check(dp.getString("sourceKey").equals(corrected),"completed date uses canonical identity");
        check(call(dates,"confirm",new JSONObject().put("id",dp.getString("id")).put("entry",dp)).getBoolean("ok"),"completed row can book");
        check(dates.scan(Arrays.asList(new BillParser.Bill("2026-09-30","12:30","测试日期店","alipay",1800))).getInt("duplicates")==1,"full-date rescan deduplicates after completion");
        dates.scan(Arrays.asList(undated,undated));check(state(dates).getJSONArray("pending").length()==2,"unknown-date same amount rows not silently merged");
        dp=state(dates).getJSONArray("pending").getJSONObject(0);invented=new JSONObject(dp.toString()).put("date","2026-09-30");
        call(dates,"editCandidate",new JSONObject().put("id",dp.getString("id")).put("entry",invented));dp=state(dates).getJSONArray("pending").getJSONObject(0);
        check(dp.getString("duplicateStatus").equals("possible"),"completion rechecks existing records");
        check(!call(dates,"confirm",new JSONObject().put("id",dp.getString("id")).put("entry",dp).put("forceDuplicate",true)).getBoolean("ok"),"exact completed identity cannot double book");
        dates.scan(Arrays.asList(new BillParser.Bill("2026-09-30","","无时间测试店","wechat",600)));
        JSONObject noTime=state(dates).getJSONArray("pending").getJSONObject(2);check(noTime.getString("time").isEmpty(),"missing time persists blank");
        check(call(dates,"editCandidate",new JSONObject().put("id",noTime.getString("id")).put("entry",noTime)).getBoolean("ok"),"edit allows blank time");
        call(dates,"dismiss",new JSONObject().put("id",noTime.getString("id")));check(state(dates).getJSONArray("pending").length()==2,"incomplete candidate can be dismissed");
        Memory optionalMemory=new Memory();LedgerStore optional=new LedgerStore(optionalMemory);
        BillParser.Bill dateOnly=new BillParser.Bill("2026-10-02","","日期完整测试店","wechat",4500);
        check(optional.scan(Arrays.asList(dateOnly)).getInt("incomplete")==0,"missing time does not count as needing completion");
        JSONObject op=state(optional).getJSONArray("pending").getJSONObject(0);
        optional=new LedgerStore(optionalMemory);
        check(call(optional,"confirm",new JSONObject().put("id",op.getString("id")).put("entry",op)).getBoolean("ok"),"date-only candidate confirms directly after reopening");
        check(state(optional).getJSONArray("entries").getJSONObject(0).getString("time").isEmpty(),"confirmation keeps blank time without invented midnight");
        JSONObject exported=new JSONObject(optional.exportBackup());LedgerStore optionalRestore=new LedgerStore(new Memory());
        check(call(optionalRestore,"importBundle",exported).getBoolean("ok")&&state(optionalRestore).getJSONArray("entries").getJSONObject(0).getString("time").isEmpty(),"blank time survives backup round trip");
        JSONObject stored=state(optionalRestore).getJSONArray("entries").getJSONObject(0).put("note","修改备注");
        check(call(optionalRestore,"upsert",stored).getBoolean("ok"),"booked date-only entry can be edited");
        check(!call(optionalRestore,"upsert",new JSONObject(stored.toString()).put("time","25:00")).getBoolean("ok"),"nonempty invalid time rejected");
        check(!call(optionalRestore,"upsert",new JSONObject(stored.toString()).put("date","")).getBoolean("ok"),"blank date still rejected when time optional");
        JSONObject repeat=optional.scan(Arrays.asList(dateOnly));
        check(repeat.getInt("added")==1&&repeat.getInt("duplicates")==0&&repeat.getInt("conflicts")==1,"dateless-time rescan preserved as possible duplicate instead of silently discarded");
        op=state(optional).getJSONArray("pending").getJSONObject(0);
        check(!call(optional,"confirm",new JSONObject().put("id",op.getString("id")).put("entry",op)).getBoolean("ok"),"possible collision needs explicit decision");
        check(call(optional,"confirm",new JSONObject().put("id",op.getString("id")).put("entry",op).put("forceDuplicate",true)).getBoolean("ok"),"distinct same-day same-amount date-only purchase can be retained");
        check(optional.scan(Arrays.asList(new BillParser.Bill("2026-10-02","19:14","日期完整测试店","wechat",4500))).getInt("conflicts")==1,"complete rescan compared against blank-time booked entry");
        LedgerStore mixedOptional=new LedgerStore(new Memory());
        mixedOptional.scan(Arrays.asList(dateOnly,dateOnly,undated,new BillParser.Bill("2026-10-03","18:00","另一店铺","wechat",800)));
        JSONArray mixedIds=new JSONArray();for(int i=0;i<state(mixedOptional).getJSONArray("pending").length();i++)mixedIds.put(state(mixedOptional).getJSONArray("pending").getJSONObject(i).getString("id"));
        JSONObject optionalBatch=call(mixedOptional,"confirmBatch",new JSONObject().put("ids",mixedIds));
        check(optionalBatch.getInt("confirmed")==2&&optionalBatch.getInt("conflicts")==1&&optionalBatch.getInt("incomplete")==1,"mixed batch books date-only, keeps missing date and possible duplicate");
        LedgerStore completion=new LedgerStore(new Memory());completion.scan(Arrays.asList(new BillParser.Bill("","","补日期测试","wechat",500)));
        op=state(completion).getJSONArray("pending").getJSONObject(0);op.put("date","2026-10-02");
        check(call(completion,"editCandidate",new JSONObject().put("id",op.getString("id")).put("entry",op)).getBoolean("ok"),"date can be completed without filling time");
        op=state(completion).getJSONArray("pending").getJSONObject(0);
        check(call(completion,"confirm",new JSONObject().put("id",op.getString("id")).put("entry",op)).getBoolean("ok"),"date completion alone enables confirmation");
        Memory legacyMemory=new Memory();JSONObject legacy=entry("legacy_no_time").put("source","scan").put("time","").put("sourceKey",ScanIdentity.key("wechat","unresolved_legacy","",345,"旧的手动记录"));
        legacyMemory.data.put("state",new JSONObject().put("schema",2).put("entries",new JSONArray()).put("pending",new JSONArray().put(legacy)).put("ignored",new JSONArray()).toString());
        LedgerStore legacyQueue=new LedgerStore(legacyMemory);
        check(call(legacyQueue,"confirm",new JSONObject().put("id",legacy.getString("id")).put("entry",legacy)).getBoolean("ok"),"v1.6.2 blank-time queue usable without rescanning");
        LedgerStore fillTime=new LedgerStore(new Memory());fillTime.scan(Arrays.asList(dateOnly));op=state(fillTime).getJSONArray("pending").getJSONObject(0);op.put("time","19:14");
        call(fillTime,"editCandidate",new JSONObject().put("id",op.getString("id")).put("entry",op));op=state(fillTime).getJSONArray("pending").getJSONObject(0);
        check(op.getString("sourceKey").equals(ScanIdentity.key("wechat","2026-10-02","19:14",4500,"日期完整测试店")),"optional time completion restores exact scan identity");
        System.out.println("Ledger storage: "+checks+" checks passed");
    }
}
