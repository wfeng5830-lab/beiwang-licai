package cn.dailyledger.app;
import org.json.JSONArray;
import org.json.JSONObject;

public final class QuadrantIconModelTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static JSONObject memo(int slot, String text) throws Exception {
        return new JSONObject().put("slot",slot).put("body",text).put("title","").put("done",false);
    }
    public static void main(String[] args) throws Exception {
        check(new QuadrantIconModel(null).isEmpty(), "empty ledger uses original icon");
        JSONArray items = new JSONArray().put(memo(-1,"未分配")).put(memo(0,"已完成").put("done",true));
        check(new QuadrantIconModel(items).isEmpty(), "unassigned and completed do not activate icon");
        items.put(memo(0,"买药").put("mark","药"));
        items.put(memo(10,"练习英语")).put(memo(9,"读书").put("mark","书"));
        QuadrantIconModel model = new QuadrantIconModel(items);
        check(model.total()==3 && model.count(0)==1 && model.count(1)==2 && model.count(2)==0, "quadrants switch independently");
        check(model.mark(1,0).equals("书") && model.mark(1,1).equals("练"), "follows slot order, not insertion order");
        items.getJSONObject(3).put("done",true);
        check(new QuadrantIconModel(items).count(1)==1, "completing one of two returns to large character");
        items.getJSONObject(4).put("slot",18);model = new QuadrantIconModel(items);
        check(model.count(1)==0 && model.count(2)==1, "moving across quadrants clears old quadrant");
        JSONArray full = new JSONArray();
        for(int i=0;i<36;i++) full.put(memo(i,"事项"+i).put("mark",String.valueOf(i%9+1)));
        model = new QuadrantIconModel(full);
        check(model.total()==36 && model.visibleCount(0)==4 && model.overflow(0)==5, "36 tasks show 4 per quadrant and overflow");
        String before = model.fingerprint();full.getJSONObject(8).put("body","改变隐藏事项");
        check(new QuadrantIconModel(full).fingerprint().equals(before), "unseen content avoids redundant refresh");
        full.getJSONObject(0).put("mark","新");
        check(!new QuadrantIconModel(full).fingerprint().equals(before), "visible mark edit refreshes");
        model = new QuadrantIconModel(new JSONArray().put(memo(0,"  😀计划")));
        check(model.mark(0,0).equals("😀"), "preserves supplementary Unicode code point");
        model = new QuadrantIconModel(new JSONArray().put(memo(0,"正文").put("title","旧标题")));
        check(model.mark(0,0).equals("旧"), "legacy title uses same initial as memo page");
        check(new QuadrantIconModel(new JSONArray().put(memo(36,"越界"))).isEmpty(), "out-of-range ignored");
        LedgerStore store = new LedgerStore(new LedgerStoreTest.Memory());
        JSONObject task=memo(0,"买药").put("id","urgent");
        store.mutate("saveMemo",task.toString());
        check(new QuadrantIconModel(new JSONObject(store.read()).getJSONArray("memos")).count(0)==1,"uses persisted task state");
        store.mutate("saveMemo",task.put("done",true).toString());
        check(new QuadrantIconModel(new JSONObject(store.read()).getJSONArray("memos")).isEmpty(),"saved completion restores default");
        System.out.println("QuadrantIconModelTest: " + checks + " checks passed");
    }
}
