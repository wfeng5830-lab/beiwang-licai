package cn.dailyledger.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Projects occupied slots in reading order; unassigned and completed tasks are excluded. */
final class QuadrantIconModel {
    private final List<List<String>> quadrants = new ArrayList<>();
    QuadrantIconModel(JSONArray memos) {
        List<JSONObject> active = new ArrayList<>();
        if (memos != null) for (int i = 0; i < memos.length(); i++) {
            JSONObject memo = memos.optJSONObject(i);
            if (memo == null || memo.optBoolean("done")) continue;
            int slot = memo.optInt("slot", -1);
            if (slot >= 0 && slot < 36) active.add(memo);
        }
        active.sort(Comparator.comparingInt(m -> m.optInt("slot")));
        for (int q = 0; q < 4; q++) quadrants.add(new ArrayList<>());
        for (JSONObject memo : active) {
            String mark = memo.optString("mark", "").trim();
            if (mark.isEmpty()) mark = memo.optString("title", "").trim();
            if (mark.isEmpty()) mark = memo.optString("body", "").trim();
            if (mark.isEmpty()) mark = "事";
            quadrants.get(memo.optInt("slot") / 9).add(mark.substring(0, mark.offsetByCodePoints(0, 1)));
        }
    }
    int total() { int count = 0; for (List<String> q : quadrants) count += q.size(); return count; }
    boolean isEmpty() { return total() == 0; }
    int count(int q) { return quadrants.get(q).size(); }
    int visibleCount(int q) { return Math.min(4, count(q)); }
    int overflow(int q) { return Math.max(0, count(q) - 4); }
    String mark(int q, int index) { return quadrants.get(q).get(index); }
    String fingerprint() {
        JSONArray result = new JSONArray();
        for (int q = 0; q < 4; q++) {
            JSONArray quadrant = new JSONArray().put(count(q));
            for (int i = 0; i < visibleCount(q); i++) quadrant.put(mark(q, i));
            result.put(quadrant);
        }
        return result.toString();
    }
}
