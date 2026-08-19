package io.github.waytosea.yunhaishijian;

import org.json.JSONObject;

/** /balance.json 的解析结果（余额 + Token 统计） */
final class BalanceData {
    static final class TrendPoint {
        String label;
        long tokens;
        TrendPoint(String label, long tokens) { this.label = label; this.tokens = tokens; }
    }

    static final class NamedValue {
        String name;
        long tokens;
        NamedValue(String name, long tokens) { this.name = name; this.tokens = tokens; }
    }

    /** 单个额度窗口（N 窗口通用结构） */
    static final class Window {
        String label;
        double pct = -1;
        long reset;
        boolean hourScale;
    }

    static final class Tool {
        double p5 = -1, p7 = -1;
        long r5, r7;
        long today = -1, week = -1, month = -1;
        final java.util.List<Window> windows = new java.util.ArrayList<>();
        final java.util.List<TrendPoint> trend = new java.util.ArrayList<>();
        final java.util.List<NamedValue> projects = new java.util.ArrayList<>();

        /** 统一出口：新结构优先，否则由旧 p5/p7 字段合成 */
        java.util.List<Window> resolvedWindows() {
            if (!windows.isEmpty()) return windows;
            java.util.List<Window> list = new java.util.ArrayList<>();
            if (p5 >= 0) { Window w = new Window(); w.label = "5时"; w.pct = p5; w.reset = r5; w.hourScale = true; list.add(w); }
            if (p7 >= 0) { Window w = new Window(); w.label = "7天"; w.pct = p7; w.reset = r7; w.hourScale = false; list.add(w); }
            return list;
        }

        /** 瓶颈窗口：剩余最少者；60 分钟内即将重置的按满额计（马上刷新的紧张无意义） */
        Window tightest() {
            long now = System.currentTimeMillis() / 1000L;
            Window best = null;
            double bestEff = Double.MAX_VALUE;
            for (Window w : resolvedWindows()) {
                if (w.pct < 0) continue;
                double eff = (w.reset > 0 && w.reset - now < 3600) ? 100 : w.pct;
                if (eff < bestEff) { bestEff = eff; best = w; }
            }
            return best;
        }

        int bottleneckIndex() {
            Window b = tightest();
            java.util.List<Window> list = resolvedWindows();
            for (int i = 0; i < list.size(); i++) if (list.get(i) == b) return i;
            return 0;
        }

        boolean hasBalance() {
            return p5 >= 0 || p7 >= 0;
        }

        boolean hasTokens() {
            return today >= 0;
        }
    }

    final Tool codex = new Tool();
    final Tool claude = new Tool();
    long fetchedAt;

    static BalanceData parse(JSONObject root) {
        BalanceData data = new BalanceData();
        fill(data.codex, root.optJSONObject("codex"));
        fill(data.claude, root.optJSONObject("claude"));
        data.fetchedAt = System.currentTimeMillis();
        return data;
    }

    private static void fill(Tool tool, JSONObject object) {
        if (object == null) {
            return;
        }
        tool.p5 = object.optDouble("p5", -1);
        tool.p7 = object.optDouble("p7", -1);
        tool.r5 = (long) object.optDouble("r5", 0);
        tool.r7 = (long) object.optDouble("r7", 0);
        tool.today = object.has("today") ? object.optLong("today") : -1;
        tool.week = object.has("week") ? object.optLong("week") : -1;
        tool.month = object.has("month") ? object.optLong("month") : -1;

        org.json.JSONArray windows = object.optJSONArray("windows");
        if (windows != null) {
            for (int i = 0; i < windows.length(); i++) {
                org.json.JSONObject w = windows.optJSONObject(i);
                if (w == null) continue;
                Window win = new Window();
                win.label = w.optString("label", "");
                win.pct = w.optDouble("pct", -1);
                win.reset = (long) w.optDouble("reset", 0);
                win.hourScale = w.optBoolean("hourScale", false);
                tool.windows.add(win);
            }
        }

        org.json.JSONArray trend = object.optJSONArray("trend");
        if (trend != null) {
            for (int i = 0; i < trend.length(); i++) {
                org.json.JSONObject p = trend.optJSONObject(i);
                if (p != null) {
                    tool.trend.add(new TrendPoint(p.optString("label", ""), p.optLong("tokens")));
                }
            }
        }
        org.json.JSONArray projects = object.optJSONArray("projects");
        if (projects != null) {
            for (int i = 0; i < projects.length(); i++) {
                org.json.JSONObject p = projects.optJSONObject(i);
                if (p != null) {
                    tool.projects.add(new NamedValue(p.optString("name", ""), p.optLong("tokens")));
                }
            }
        }
    }
}
