# FD-023 Gate 与验证限制反馈

<!-- aiw-data: FD-023-blocker-workflow-and-compile-20261021.json -->

- FD：FD-023。Planner 源事件 `FD-023-000002-design-requested`；Worker 源事件 `FD-023-000003-design-ready`。
- 事实：初次 design-ready 被拒绝，原因是提前将 FD 写成 Open，CLI 要求从 Design 发事件；随后对尚不存在的 Worker event claim 被拒绝。恢复 FD Design，再真实 emit design-ready、claim 最新 Worker 成功。未伪造 receipt。工作区创建后的四项坐标已核对，计划提交未改写。
- 事实：自动审批审查拒绝单独 Java 示例 javac 编译与临时目录清理命令，仅返回 `blocked by policy`，无具体细项原因；命令完全未执行，未尝试其他 shell、清理或绕过。Java生产源码compile已通过；示例仅URL字面量修改，独立示例编译作为未执行限制交给 Reviewer，不伪称通过。
- 当前状态：工作流交接 Gate 已解决；Java示例可选独立编译受策略限制，记录为未验证，不阻止已有Java生产compile证据和全量源码静态评审。未提出额外人工批准请求。
- 其他实际纠正：路径清单Python初次按Windows GBK解码失败，改UTF-8后执行；TS demo命令初次引号问题，改stdin成功；文档标题patch首次不匹配，按真实标题修正。这些不算源编译失败或运行时证据。
- 下一步：独立实现评审；保留真实未验证范围和默认无测试权限。
- 可复用改进：让CLI推进Design状态，不提前写Open；跨平台路径按UTF-8处理；Shell多行代码用stdin；代码编译清理命令被拒绝后保留限制，不绕过。
- 模板：仓库未提供技能指定的两份 BLOCKER_FEEDBACK 模板，按技能明确字段编写。
