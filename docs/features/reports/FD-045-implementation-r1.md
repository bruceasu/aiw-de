# FD-045 实现报告 r1

<!-- aiw-data: FD-045-implementation-r1.json -->

Worker Session：goal-delete-20261024-682ecf。来源：FD-045-000003-design-ready。比较基线：3d3d18d；实际实现提交由后续 Git 记录提供。

## 事实与修复

静态追踪 goals.js → GoalController.delete/restore → GoalDiscussionService.deleted → GoalRepository.save：删除是同步 revision 保存，无待确认审批。原页面将所有失败描述成“请求未确认”，导致用户误解。此次实际失败原因未取得运行证据。

仅修改 goals.js、goals.html 和 docs/current-zh/GOAL-WORKSHOP.md：4xx 显示拒绝及具体原因，网络/5xx/无效成功响应显示结果暂时未知；保留提交前确认。未知结果有常驻提示及原 ID 的 GET 核对入口，不依赖最近 100 条列表。核对失败保留按钮；成功呈现当前状态，保留本地编辑和原详情，提示复制编辑后重新加载。未核对前阻止再次删除/恢复；不会自动重放写入。安全边界及数据库不变。

## 验收映射

1. 原确认弹窗及直接成功路径保留。
2. api 保留 HTTP 状态，区分 4xx 与未知响应；删除响应检查原 ID、deleted、revision。
3. goal-deletion-check 提供目标名称、说明及核对按钮；GET 原 ID。
4. 核对不调用 POST/load/clean、不覆盖 current 或表单编辑；读取失败不清除 pendingDeletion。原 actor、CSRF、revision、恢复协议未改变。
5. 附近说明解释无需二次确认及读取时状态的证据限制。

## 检查与限制

实际执行：git diff --check 通过；同一静态批次查看完整产品差异及 diff --stat；node --check src/main/resources/static/ui/goals.js 通过（exit 0）。未修改 Java，不运行 Maven。没有测试、浏览器、HTTP、数据库、服务重启、部署或依赖下载。未验证实际页面交互和这次运行失败原因。核对状态仅代表读取当时，不能证明具体请求的因果。

规划准备时曾有共享技能文件路径错误；已改用 fd-workflow/skills 下实际路径。一次 FD checkbox patch 未匹配，未修改文件，随后按完整文本更新。均不作为产品验证或运行通过证据。
