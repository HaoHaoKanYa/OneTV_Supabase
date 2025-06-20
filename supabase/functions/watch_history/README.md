# 观看历史 Edge Function

这个Edge Function用于处理观看历史数据的加载，提供了分页、过滤和统计功能，减轻客户端的负担并提高性能。

## 功能

- 分页加载观看历史数据
- 按时间范围过滤（全部、今天、本周、本月、今年）
- 按不同字段排序（时间、频道名、观看时长）
- 获取观看统计数据（总观看时长、观看频道数、观看次数、最常观看频道）
- 记录用户观看历史

## API 端点

基础URL: `https://<project-ref>.supabase.co/functions/v1/watch_history`

### 获取观看历史列表

```
GET /watch_history?action=list
```

参数:
- `page`: 页码 (默认: 1)
- `pageSize`: 每页条目数 (默认: 20)
- `timeRange`: 时间范围 ('all', 'today', 'week', 'month', 'year')
- `sortBy`: 排序字段 ('watch_start', 'channel_name', 'duration')
- `sortOrder`: 排序方向 ('asc', 'desc')

响应:
```json
{
  "items": [
    {
      "id": "uuid",
      "channel_name": "CCTV-1",
      "channel_url": "http://example.com/stream",
      "watch_start": "2023-01-01T12:00:00",
      "watch_end": "2023-01-01T13:00:00",
      "duration": 3600
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "totalItems": 100,
    "totalPages": 5
  }
}
```

### 获取观看统计

```
GET /watch_history?action=statistics
```

参数:
- `timeRange`: 时间范围 ('all', 'today', 'week', 'month', 'year')

响应:
```json
{
  "statistics": {
    "totalWatchTime": 36000,
    "totalChannels": 15,
    "totalWatches": 100,
    "mostWatchedChannel": "CCTV-1",
    "channelStatistics": [
      {
        "channelName": "CCTV-1",
        "watchCount": 20,
        "totalDuration": 10000
      }
    ]
  }
}
```

### 记录观看历史

```
POST /watch_history
```

请求体:
```json
{
  "channelName": "CCTV-1",
  "channelUrl": "http://example.com/stream",
  "duration": 3600
}
```

响应:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "channel_name": "CCTV-1",
      "channel_url": "http://example.com/stream",
      "watch_start": "2023-01-01T12:00:00",
      "watch_end": "2023-01-01T13:00:00",
      "duration": 3600,
      "user_id": "user-uuid",
      "created_at": "2023-01-01T13:00:00"
    }
  ]
}
```

## 部署

使用以下命令部署此Edge Function:

```bash
supabase functions deploy watch_history --project-ref <project-ref>
```

## 安全

此Edge Function需要认证才能访问，确保在请求中包含有效的JWT令牌。 