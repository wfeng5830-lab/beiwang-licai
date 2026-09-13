package cn.dailyledger.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class LedgerStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    LedgerStore(Context context) { prefs = context.getSharedPreferences("ledger", Context.MODE_PRIVATE); }
    private JSONObject load() throws Exception {
        JSONObject state=new JSONObject(prefs.getString("state", "{\"entries\":[],\"pending\":[]}"));
        if(state.optInt("schema")<2){JSONArray keep=new JSONArray(),old=state.getJSONArray("pending");for(int i=0;i<old.length();i++)if("scan".equals(old.getJSONObject(i).optString("source")))keep.put(old.getJSONObject(i));state.put("pending",keep).put("ignored",new JSONArray()).put("schema",2);state.remove("seen");save(state);}
        if(!state.has("memos"))state.put("memos",new JSONArray());
        return state;
    }
    String read() throws Exception { synchronized (LOCK) { return load().toString(); } }
    private void save(JSONObject state) throws Exception {
        if (!prefs.edit().putString("state", state.toString()).commit()) throw new Exception("手机存储写入失败，请检查可用空间");
    }
    private JSONObject validate(JSONObject input) throws Exception {
        String id=input.getString("id"),date=input.getString("date"),time=input.getString("time");
        if(!id.matches("[a-zA-Z0-9_-]{1,100}")) throw new Exception("账单编号无效");
        Object amount = input.get("cents");
        if (!(amount instanceof Number) || ((Number)amount).doubleValue() != ((Number)amount).longValue()) throw new Exception("金额无效");
        long cents=((Number)amount).longValue();
        if(cents<=0 || cents>999999999L) throw new Exception("金额超出范围");
        if(!date.matches("20\\d\\d-\\d{2}-\\d{2}") || !LocalDate.parse(date).toString().equals(date)) throw new Exception("日期无效");
        if(!time.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new Exception("时间无效");
        LocalTime.parse(time);
        String channel=input.getString("channel"),category=input.getString("category"),note=input.getString("note");
        if(!Arrays.asList("wechat","alipay","other").contains(channel)) throw new Exception("支付方式无效");
        if(!Arrays.asList("餐饮","购物","交通","生活缴费","娱乐","医疗","其他").contains(category)) throw new Exception("分类无效");
        if(note.length()>80) throw new Exception("备注最多 80 字");
        String source=input.optString("source");if(!Arrays.asList("notification","scan").contains(source))source="manual";
        JSONObject result=new JSONObject().put("id",id).put("date",date).put("time",time).put("cents",cents).put("channel",channel).put("category",category).put("note",note).put("source",source);
        if(input.has("sourceKey")){String key=input.getString("sourceKey");if(!key.matches("[a-f0-9]{64}"))throw new Exception("扫描来源无效");result.put("sourceKey",key);}return result;
    }
    private int find(JSONArray entries, String id) throws Exception {
        for(int i=0;i<entries.length();i++) if(entries.getJSONObject(i).getString("id").equals(id)) return i;
        return -1;
    }
    private JSONObject validateMemo(JSONObject input)throws Exception{
        String id=input.getString("id"),title=input.getString("title").trim(),body=input.getString("body");
        if(!id.matches("[a-zA-Z0-9_-]{1,100}")||title.length()>100||body.length()>10000||(title.isEmpty()&&body.trim().isEmpty()))throw new Exception("备忘录需填写内容，标题最多 100 字，正文最多 10000 字");
        return new JSONObject().put("id",id).put("title",title).put("body",body);
    }
    String mutate(String action, String payload) {
        synchronized (LOCK) {
            try {
                JSONObject state=load();JSONArray entries=state.getJSONArray("entries"),pending=state.getJSONArray("pending");
                if ("import".equals(action)||"importBundle".equals(action)) {
                    JSONObject bundle="importBundle".equals(action)?new JSONObject(payload):null;
                    JSONArray incoming=bundle==null?new JSONArray(payload):bundle.getJSONArray("entries");if(incoming.length()>50000)throw new Exception("备份记录过多");
                    Set<String> ids=new HashSet<>();for(int i=0;i<entries.length();i++)ids.add(entries.getJSONObject(i).getString("id"));
                    for(int i=0;i<incoming.length();i++){JSONObject entry=validate(incoming.getJSONObject(i));if(ids.add(entry.getString("id"))&&exact(entries,entry)<0)entries.put(entry);}
                    if(bundle!=null){JSONArray incomingMemos=bundle.getJSONArray("memos"),memos=state.getJSONArray("memos");if(incomingMemos.length()>1000)throw new Exception("备忘录过多");for(int i=0;i<incomingMemos.length();i++){JSONObject memo=validateMemo(incomingMemos.getJSONObject(i));if(find(memos,memo.getString("id"))<0)memos.put(memo);}}
                } else {
                    JSONObject data=new JSONObject(payload);
                    if("saveMemo".equals(action)){
                        JSONObject memo=validateMemo(data);JSONArray memos=state.getJSONArray("memos");int index=find(memos,memo.getString("id"));if(index<0)memos.put(memo);else memos.put(index,memo);
                    }else if("deleteMemo".equals(action)){JSONArray memos=state.getJSONArray("memos");int index=find(memos,data.getString("id"));if(index>=0)memos.remove(index);
                    }else if("upsert".equals(action)) {
                        JSONObject entry=validate(data);int index=find(entries,entry.getString("id"));
                        if(index<0)entries.put(entry);else {JSONObject old=entries.getJSONObject(index);if(old.has("sourceKey"))entry.put("sourceKey",old.getString("sourceKey")).put("source",old.optString("source","scan"));entries.put(index,entry);}
                    } else if("editCandidate".equals(action)) {
                        int index=find(pending,data.getString("id"));if(index<0)throw new Exception("该扫描记录已处理");
                        JSONObject original=pending.getJSONObject(index),edited=validate(data.getJSONObject("entry"));
                        if(!original.getString("id").equals(edited.getString("id")))throw new Exception("扫描编号不一致");
                        edited.put("source","scan").put("sourceKey",original.getString("sourceKey"));int match=possible(entries,edited);
                        edited.put("duplicateStatus",match>=0?"possible":"new");if(match>=0)edited.put("duplicateId",entries.getJSONObject(match).getString("id"));pending.put(index,edited);
                    } else if("delete".equals(action)) {int index=find(entries,data.getString("id"));if(index>=0)entries.remove(index);}
                    else if("confirm".equals(action)) {
                        int index=find(pending,data.getString("id"));if(index<0)throw new Exception("该扫描记录已经处理，请刷新");
                        JSONObject original=pending.getJSONObject(index),entry=validate(data.getJSONObject("entry"));
                        if(!original.getString("id").equals(entry.getString("id")))throw new Exception("扫描编号不一致");
                        entry.put("source","scan").put("sourceKey",original.getString("sourceKey"));
                        if(find(entries,entry.getString("id"))>=0||exact(entries,entry)>=0)throw new Exception("这笔原始扫描账单已经入账，请忽略重复记录");
                        int conflict=possible(entries,entry);
                        if(conflict>=0&&!data.optBoolean("forceDuplicate")){original.put("duplicateStatus","possible").put("duplicateId",entries.getJSONObject(conflict).getString("id"));save(state);return new JSONObject().put("ok",false).put("code","possible_duplicate").put("error","疑似重复，请对比已有账单；若是另一笔消费，请勾选确认").toString();}
                        entries.put(entry);pending.remove(index);
                    } else if("confirmBatch".equals(action)) {
                        JSONArray ids=data.getJSONArray("ids");if(ids.length()>1000)throw new Exception("批量数量过多");int confirmed=0,duplicates=0,conflicts=0;
                        for(int i=0;i<ids.length();i++){int index=find(pending,ids.getString(i));if(index<0)continue;JSONObject entry=validate(pending.getJSONObject(index));
                            if(exact(entries,entry)>=0||find(entries,entry.getString("id"))>=0){pending.remove(index);duplicates++;continue;}
                            int match=possible(entries,entry);if(match>=0){pending.getJSONObject(index).put("duplicateStatus","possible").put("duplicateId",entries.getJSONObject(match).getString("id"));conflicts++;continue;}
                            if(entries.length()>=50000)throw new Exception("账本已满，请先备份");entries.put(entry);pending.remove(index);confirmed++;
                        }
                        save(state);return new JSONObject().put("ok",true).put("confirmed",confirmed).put("duplicates",duplicates).put("conflicts",conflicts).toString();
                    } else if("dismiss".equals(action)) {int index=find(pending,data.getString("id"));if(index>=0){JSONArray ignored=state.getJSONArray("ignored");ignored.put(pending.getJSONObject(index).getString("sourceKey"));if(ignored.length()>10000)ignored.remove(0);pending.remove(index);}}
                    else throw new Exception("未知操作");
                }
                if(entries.length()>50000)throw new Exception("账本最多支持 50000 笔，请先导出备份");
                if(state.getJSONArray("memos").length()>1000)throw new Exception("最多保存 1000 条备忘录");
                save(state);return "{\"ok\":true}";
            }catch(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()==null?"账本写入失败":e.getMessage()).toString();}catch(Exception ignored){return "{\"ok\":false}";}}
        }
    }
    private int exact(JSONArray entries,JSONObject e) throws Exception {
        String key=e.optString("sourceKey");if(key.isEmpty())return -1;
        for(int i=0;i<entries.length();i++)if(key.equals(entries.getJSONObject(i).optString("sourceKey")))return i;return -1;
    }
    private int possible(JSONArray entries,JSONObject e)throws Exception{
        for(int i=0;i<entries.length();i++){JSONObject old=entries.getJSONObject(i);if(old.getString("channel").equals(e.getString("channel"))&&old.getString("date").equals(e.getString("date"))&&old.getString("time").equals(e.getString("time"))&&old.getLong("cents")==e.getLong("cents"))return i;}return -1;
    }
    JSONObject scan(java.util.List<BillParser.Bill> bills) throws Exception {
        synchronized (LOCK) {
            JSONObject state=load();JSONArray pending=state.getJSONArray("pending"),entries=state.getJSONArray("entries"),ignored=state.getJSONArray("ignored");
            Set<String> dismissed=new HashSet<>();for(int i=0;i<ignored.length();i++)dismissed.add(ignored.getString(i));
            int added=0,duplicates=0,conflicts=0,overflow=0;
            for(BillParser.Bill b:bills){String key=ScanIdentity.key(b.channel,b.date,b.time,b.cents,b.note);
                JSONObject e=new JSONObject().put("id","s_"+key).put("sourceKey",key).put("source","scan").put("cents",b.cents).put("date",b.date).put("time",b.time).put("channel",b.channel).put("note",b.note).put("category","其他");e=validate(e);
                if(dismissed.contains(key)||exact(entries,e)>=0||exact(pending,e)>=0){duplicates++;continue;}
                if(pending.length()>=1000){overflow++;continue;}
                int match=possible(entries,e);e.put("duplicateStatus",match>=0?"possible":"new");if(match>=0){e.put("duplicateId",entries.getJSONObject(match).getString("id"));conflicts++;}
                pending.put(e);added++;
            }
            save(state);return new JSONObject().put("added",added).put("duplicates",duplicates).put("conflicts",conflicts).put("overflow",overflow);
        }
    }
    String exportBackup() throws Exception {synchronized(LOCK){JSONObject s=load();return new JSONObject().put("version",1).put("entries",s.getJSONArray("entries")).put("memos",s.getJSONArray("memos")).toString(2);}}
}
