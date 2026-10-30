package com.example.dsh.ui.home

import com.example.dsh.bus.BusStore
import com.example.dsh.bus.DescribeDecision
import com.example.dsh.bus.busParse

/**
 * 仅用于首页预览和单测的本地快照。
 *
 * 它走的是和真实数据相同的 BusStore -> toHomeView 链路，
 * 只是来源不是隧道。接上 AgentHostBus 后可以删除这个 fixture。
 */
internal fun previewBusHomeView(): BusHomeView {
    val store = BusStore()
    store.bindDescribe(DescribeDecision.AgentHost("preview-host", "3.9", true))
    store.applySnapshot(busParse(BUS_HOME_FIXTURE_JSON))
    return store.toHomeView()
}

internal val BUS_HOME_FIXTURE_JSON = """
    {
      "agents": [
        {
          "agentId": "preview-host",
          "name": "Mac mini",
          "platform": "macos",
          "online": true,
          "lastSeenAt": "2026-10-29T08:30:00Z",
          "appVersion": "1.2.0",
          "canRemoveProjects": true,
          "connectors": [
            {"kind":"acp","connectorId":"claude-code","displayName":"Claude Code","available":true,"enabled":true,"status":"ok","taskCount":1,"canDeleteTasks":true},
            {"kind":"codex","displayName":"Codex","available":true,"enabled":true,"status":"ok","taskCount":1,"canDeleteTasks":true},
            {"kind":"dsh","displayName":"DeepSeek Harness","available":true,"enabled":true,"status":"ok","taskCount":2},
            {"kind":"acp","connectorId":"opencode","displayName":"OpenCode","available":true,"enabled":true,"status":"ok","taskCount":1}
          ]
        }
      ],
      "tasks": [
        {
          "id": "acp:claude-code:s1",
          "agentId": "preview-host",
          "source": "acp",
          "connectorId": "claude-code",
          "title": "合并前端打包优化 PR",
          "projectPath": "/Users/me/Projects/agentbus",
          "projectName": "agentbus",
          "status": "waitingApproval",
          "lastMessage": "请求：合并 PR #42",
          "origin": "desktop",
          "controllable": true,
          "startedAt": "2026-10-29T08:20:00Z",
          "updatedAt": "2026-10-29T08:29:00Z",
          "taskType": "code"
        },
        {
          "id": "codex:s2",
          "agentId": "preview-host",
          "source": "codex",
          "title": "重构 Bus 缓存层",
          "projectPath": "/Users/me/Projects/agentbus",
          "projectName": "agentbus",
          "status": "running",
          "lastMessage": "正在写单元测试…",
          "origin": "desktop",
          "controllable": true,
          "startedAt": "2026-10-29T08:05:00Z",
          "updatedAt": "2026-10-29T08:20:00Z",
          "taskType": "code"
        },
        {
          "id": "dsh:s3",
          "agentId": "preview-host",
          "source": "dsh",
          "title": "排查 Relay 连接超时",
          "projectPath": "/Users/me/Projects/agentbus",
          "projectName": "agentbus",
          "status": "waitingInput",
          "lastMessage": "提问：先看哪份日志？",
          "origin": "watch",
          "controllable": true,
          "startedAt": "2026-10-29T07:40:00Z",
          "updatedAt": "2026-10-29T08:10:00Z",
          "taskType": "diagnose"
        },
        {
          "id": "acp:opencode:s4",
          "agentId": "preview-host",
          "source": "acp",
          "connectorId": "opencode",
          "title": "更新组件库文档",
          "projectPath": "/Users/me/Projects/ui-kit",
          "projectName": "ui-kit",
          "status": "completed",
          "lastMessage": "3 个文件已更新",
          "origin": "desktop",
          "controllable": true,
          "startedAt": "2026-10-28T16:50:00Z",
          "updatedAt": "2026-10-28T17:26:00Z",
          "taskType": "doc"
        },
        {
          "id": "dsh:s5",
          "agentId": "preview-host",
          "source": "dsh",
          "title": "整理 KMP 学习笔记",
          "projectPath": "/Users/me/Projects/notes",
          "projectName": "notes",
          "status": "completed",
          "lastMessage": "笔记已保存到项目目录",
          "origin": "watch",
          "controllable": true,
          "startedAt": "2026-10-22T10:45:00Z",
          "updatedAt": "2026-10-22T11:30:00Z",
          "taskType": "note"
        }
      ],
      "projects": [
        {"agentId":"preview-host","path":"/Users/me/Projects/agentbus","name":"agentbus","lastUsedAt":"2026-10-29T08:29:00Z","pinned":false},
        {"agentId":"preview-host","path":"/Users/me/Projects/ui-kit","name":"ui-kit","lastUsedAt":"2026-10-28T17:26:00Z","pinned":false},
        {"agentId":"preview-host","path":"/Users/me/Projects/notes","name":"notes","lastUsedAt":"2026-10-22T11:30:00Z","pinned":false}
      ],
      "recentResults": [],
      "recentMessages": [],
      "seq": 12,
      "generatedAt": "2026-10-29T08:30:00Z"
    }
""".trimIndent()
