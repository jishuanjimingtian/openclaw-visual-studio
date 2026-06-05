# OpenClaw Chat Attachments Protocol

Visual Studio sends multimodal messages to Gateway via `chat.send` with structured `content` parts.

## Preferred payload

```json
{
  "sessionKey": "agent:main:main",
  "deliver": false,
  "idempotencyKey": "<run-uuid>",
  "content": [
    { "type": "text", "text": "请分析这张图" },
    {
      "type": "image",
      "path": "/absolute/path/to/workspace/.openclaw-media/chat/2026-06/<uuid>/original.jpg",
      "name": "photo.jpg",
      "mime": "image/jpeg"
    }
  ],
  "message": "请分析这张图\n[附件] photo.jpg (...)"
}
```

## Fallback

If Gateway ignores `content`, the string `message` field still contains path hints for agent tools.

## History

`chat.history` may return `content[]` with `image` / `file` parts. VS strips inline base64 and exposes only metadata (`attachmentId`, `name`, `mime`, `sizeBytes`) to the UI.
