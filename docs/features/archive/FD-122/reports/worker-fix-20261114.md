# FD-122 Worker 修复报告

- **来源事件：** `FD-122-000004-changes-requested`
- **Worker 会话：** `fd122-worker-fix-20261114`
- **修复内容：** 抽出 `startedNode` 判断，统一检查访问计数及已有的 `DISPATCHED`、`QUESTION` 事件。节点选择和新路线校验共用该判断；因此已提问的 ASK 节点不能被当作未访问节点重新路由。确认应用时仍会重新核验所有候选。
- **边界：** 仅阻止新增指向已启动节点的路线；原工作流已经存在的路线不被追溯改写。

## 验证

- 离线 Java 编译通过：`mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests compile`（JDK 25）。
- `git diff --check` 通过。
- 未运行测试、模型、服务、迁移或浏览器。
- 首轮独立审查发现的问题已修复；等待聚焦复核。
