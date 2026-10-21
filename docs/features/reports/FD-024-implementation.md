# FD-024 实现交接

<!-- aiw-data: FD-024-implementation.json -->

Worker：`01a15e5f-d79d-73e0-bb3b-480e76aa474c`。源事件：`FD-024-000006-design-ready`。比较基线：`c182720`；实现提交：`5bff2c1`、`b893b92`、`233b403`。文档与编译入口随本报告单独提交。

## 已实现的事实

- 完整覆盖 FD 的实现项。平台 `authority` 模块负责人工身份、个人 Worker 专用凭证、会话世代、最小能力、独立读范围、禁止投影、累计预算、动作回执及实际任务启动准入。20 个子动作连接原业务服务，保留角色、发布 attempt、模型及专业独立性边界。
- 平台数据库只保存最小权威记录。独立 `workers/personal-secretary` 提供身份绑定私库、历史版本、私人 UI、调度、模型进程、策略与原子决策/outbox；响应丢失按原 requestId 查询，UNKNOWN 不自动重放或退款。
- 已接管 Plan 的实际完成先记录事实，继续推进需要新的有效准入。撤权、暂停、接管、世代变化与当前禁止投影在实际启动时复查。
- 移除旧秘书服务、仓储、调度、API、页面和三个旧 Worker profile；没有双模式或旧模式适配层。保留历史 V8/V9 SQL，新增 V10 权威表和旧数据封存 Gate。
- 迁移工具仅接受明确提供的离线只读快照，形成预览、内存候选与导入规划；未映射和关联不完整记录默认封存。实际导出、导入、清理、回退及切换执行入口均拒绝。
- 更新四份稳定规范、平台和个人 Worker 操作文档、FD 工作项及验收映射。此前设计评审只作为设计历史，不代表实现审核通过。

用户已明确继续完整 FD，迁移仅实现工具且禁止实际迁移/切换，并取消旧模式兼容。既有两份 blocker 分别为 resolved 与 resolved-for-tool-implementation；其恢复接口、缺失模板及执行 Gate 分离的改进建议保留为后续独立范围。

## 实际检查

2026-10-21 23:03:38 JST，当前源码的 compile-only 入口通过。Java 上一次相关源码编译于 23:02:16 通过，本次 Maven 报告 up-to-date；Python 内存编译、三个 JavaScript 语法检查和修改的 TypeScript profile 语法转换再次通过。

```powershell
$fd024PreviousNodePath=$env:NODE_PATH
try {
  $env:NODE_PATH='D:/03_projects/AI-tools/aiw-de/workers/process-typescript/node_modules'
  & ./scripts/compile-personal-secretary.ps1 -Maven C:/green/apache-maven-3.9.9/bin/mvn.cmd -JavaHome C:/green/jdk-25.0.1+8
} finally { $env:NODE_PATH=$fd024PreviousNodePath }
```

入口运行离线生产 Java compile、Python `compile`、Node `--check` 和 TypeScript `transpileModule`；没有执行类型检查或测试。首次 Maven 参数被 PowerShell 拆分，未进入源码编译；给 `-D` 参数加引号后修正执行成功。已有 Jansi/Guava 和 Maven 工具警告不作为业务运行证据。

源码静态核对范围包括认证路由、权限与预算、事务及幂等、实际 start、私有数据载荷、迁移只读及封存边界、旧实现引用和导航。交接前执行 `git diff --check`，结果另见同名 JSON。

## 假设与未验证行为

没有运行测试、应用、浏览器交互、HTTP、模型、真实数据库/SQL migration、并发或故障注入；没有网络下载、最终 jar、发布、部署或数据切换。编译通过不证明这些行为已经运行通过。

参考进程每次启动声明新 boot 并默认停用；自定义实现隐藏物理重启无法由普通凭证远程证明。个人模型的自然语言建议不作为平台授权；平台只认固定能力。真实迁移所需的 owner 映射、备份、在途清单与回退证据仍属于未来执行 Gate。

SQL 参数、Spring 装配、并发事务顺序及 UI 完整链路只有静态证据，需独立 Reviewer 对照实际 diff 和 FD 判断是否存在可确认缺陷；本报告不是审核通过或上线结论。
