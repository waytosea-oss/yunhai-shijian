# API 数据契约

Web 与 Android 客户端只依赖三个只读接口。内置服务同时提供 `/api/*` 与旧终端兼容别名，响应应支持 `GET` 和 `HEAD`。

## 云图

```http
GET /api/cloud
GET /image.jpg
```

返回 `image/jpeg` 或 `image/png`。建议输出适合横屏显示的图片，并设置合理缓存策略。云图的授权与来源说明由部署方负责。

## 诗签与判读

```http
GET /api/poem
GET /cloud-poem.json
```

最小示例：

```json
{
  "title": "海上青云",
  "lines": ["吴淞云接海", "潮影逐风东", "远岫开青眼", "人间望碧空"],
  "poemDateShanghai": "2026-08-19",
  "analysis": {
    "phenomenon": "华东近海可见带状云系",
    "trend": "连续帧显示云带向东移动",
    "summary": "依据四帧的位置与组织变化综合判断",
    "localImpact": "上海与长江口云量变化需结合地面资料确认",
    "worldEcho": "诗意化的国际新闻映照，不承担新闻摘要功能",
    "confidence": "中"
  },
  "evidence": {
    "frameTimes": ["08:00", "10:00", "12:00", "14:00"],
    "typhoon": null
  }
}
```

约束：

- `lines` 必须恰好四项，每项恰好五个汉字，不加标点。
- `confidence` 建议使用 `低`、`中`、`高`。
- `frameTimes` 按时间正序排列。
- 没有权威快讯时，`typhoon` 必须为 `null`。
- 有权威依据时，`typhoon` 可包含 `name`、`source`、`bulletinUrl` 和 `verifiedAt`。
- `worldEcho` 是文化表达，不应伪造事实或替代新闻来源。

## 算力码表

```http
GET /api/balance
GET /balance.json
```

示例：

```json
{
  "updatedAt": 1787119200000,
  "codex": {
    "windows": [
      { "label": "5时", "pct": 62, "reset": 1787127480, "hourScale": true },
      { "label": "7天", "pct": 48, "reset": 1787499600, "hourScale": false }
    ],
    "today": 4267895,
    "week": 51813853,
    "month": 68045463,
    "trend": [{ "label": "8/19", "tokens": 4267895 }],
    "projects": [{ "name": "云图多帧判读", "tokens": 2108395 }]
  },
  "claude": {
    "windows": [
      { "label": "5时", "pct": 84, "reset": 1787129760, "hourScale": true },
      { "label": "周·全部", "pct": 42, "reset": 1787550000, "hourScale": false }
    ]
  }
}
```

字段语义：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `updatedAt` | number | 数据更新时间，Unix 秒或毫秒均可 |
| `windows[].label` | string | 额度窗口名称 |
| `windows[].pct` | number | **剩余额度百分比**，范围 0 到 100 |
| `windows[].reset` | number | 刷新时间，Unix 秒 |
| `windows[].hourScale` | boolean | `true` 时以小时/分钟显示倒计时 |
| `today/week/month` | number | 可选的聚合 Token 数 |
| `trend` | array | 最多展示最近 14 项 |
| `projects` | array | 最多展示前 4 项 |

旧版 `p5/r5/p7/r7` 字段仍可被客户端读取，但新接入应使用 `windows`。

## 错误与缓存

- JSON 接口出错时返回非 2xx 状态与 `{ "error": "..." }`。
- 云图可以短时缓存；诗签与码表建议 `no-store` 或短缓存。
- 客户端每 15 分钟静默刷新一次，也可用右下角按钮手动刷新。
