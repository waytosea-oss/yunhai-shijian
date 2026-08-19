# 每日自动更新

稳定的每日流程应拆成五步，每一步都留下可检查的产物：

1. 获取至少两帧、最好四帧已授权云图；
2. 获取权威天气、台风和必要的新闻摘要；
3. 调用 `backend/generate-poem.mjs` 生成 `runtime/poem.json`；
4. 原子替换云图、诗签和算力 JSON；
5. 等页面刷新后截图，并把图片与诗签 JSON归档。

## 生成命令

```bash
OPENAI_API_KEY="..." \
YUNHAI_CONTEXT_FILE="runtime/context.txt" \
YUNHAI_POEM_OUTPUT="runtime/poem.json" \
npm run generate -- runtime/frame-1.jpg runtime/frame-2.jpg runtime/frame-3.jpg runtime/frame-4.jpg
```

上下文文件建议只包含可核对事实：发布时间、机构、台风名称、预警级别、新闻摘要与来源 URL。生成器会验证四行五字，但仍应在公开展示前检查事实与诗意是否匹配。

## 调度建议

- macOS 使用 `launchd`；
- Linux 使用 `systemd timer`；
- GitHub 仓库不适合保存个人 API Key、受限云图或私人余额，因此不要直接用公开 Actions 生成真实日更内容；
- 任务应在网络失败时保留上一份有效数据，不要把半写入 JSON 暴露给屏幕。

建议调度时间晚于目标云图和权威快讯的正常发布时间，并为网络抖动保留一次重试。

## 截图与 Obsidian

截图属于部署层，不写死在应用中。推荐让浏览器自动化工具依次：

1. 打开主屏并等待 `/api/cloud`、`/api/poem`、`/api/balance` 完成；
2. 截取主屏；
3. 单击诗签，截取判读页；
4. 单击算力码表，截取码表页；
5. 以 `YYYY-MM-DD-主屏.png` 等稳定文件名保存；
6. 再复制到个人 Obsidian 库的附件目录，并创建一篇只引用相对路径的日记 Markdown。

公开仓库不包含任何个人 iCloud 或 Obsidian 绝对路径。部署时用环境变量传入归档目录，并确认 iCloud 已完成同步后再结束任务。

## 健康检查

```bash
curl http://127.0.0.1:8790/health
curl http://127.0.0.1:8790/api/poem
curl http://127.0.0.1:8790/api/balance
```

只有三个接口都可读、图片非空、诗句满足四行五字时，才应把当日任务标记为完成。
