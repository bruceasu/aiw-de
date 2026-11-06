# FD-077 独立静态审查

- 结论：PASS，未发现阻塞交付的缺陷。
- Reviewer session：`fd077-reviewer-20261107-01`。
- Source event：`FD-077-000002-review-requested`；认领前确认状态为 pending，未接管其他会话。
- 范围：`40c38fd..551fd4b`，主实现 `f45debd`；同时审阅 request-review 后的 FD Revision 2。工作区为当前仓库直接工作区。
- Evidence policy：Markdown。仅静态审查，没有执行测试或重跑编译。

## 发现与判断

无阻塞发现。

1. `WorkflowRecommendationService.get` 在绑定元数据之前保留 requestKey/inputVersion、理由类型和长度的校验，并要求 plan 是对象。复制 JSON 对象后，goal 取冻结任务 Envelope，source 固定 AI，模型回传或省略的元数据不会改动原始目标。
2. `WorkflowPlanSpec` 的构造校验继续约束步骤数量、key、依赖顺序和指令；真实 `WorkflowEditorService.validateCandidate` 继续约束角色、skillRef 和能力组合。新增 location 拒绝分支，既有 repositoryRef/baseRevision 拒绝分支保留。读取建议仍不创建、保存、确认或执行 Plan。
3. `owned` 保留操作者及 subject 关联校验。`recover` 通过同一个 get 读取已存完成报告，不修改历史报告或重新请求模型。前端恢复后重新轮询结果，并保留输入版本、目标及过期结果检查，人工应用和确认流程未改变。
4. `WorkflowRecommendationResultTest` 的七个用例覆盖改写/缺省元数据恢复、请求与版本关联拒绝、理由和 plan 结构、目录与仓库边界、非法角色/依赖及操作者隔离。用例使用真实候选校验器，任务与报告仓库使用 mock，未接入数据库、进程或模型。用例仅编译，不能据此称运行通过。
5. 邻近说明与实现一致，明确旧结果重新解析仍受其他校验约束。变更范围未包含用户的 `scripts/startup.bat` 或 `application-sqlite.yaml`。

## 实际证据与命令

- Reviewer 实际使用 `Get-Content`、`rg`、`git status --short`、`git diff 40c38fd..551fd4b` 和 `aiw fd show FD-077` 审阅 FD、差异、领域构造器、恢复/报告读取路径及前端应用流程。
- `aiw fd claim FD-077 FD-077-000002-review-requested --session fd077-reviewer-20261107-01` 成功。
- 已核对 Worker 在本轮提供的实际离线编译输出：`mvn.cmd -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 成功，321 份主源码与 55 份测试源码编译完成；Reviewer 没有重复执行。
- 两个初始只读定位使用了不存在的 receipt/task-mode 文件名；随后按实际目录定位到 `events/000002-review-requested.json` 与 `task-modes/code-review.md`。未执行任何替代运行验证。

## 未验证项和剩余风险

没有执行单元测试、服务启动/重启、浏览器、网络、模型调用或最终制品构建；没有数据库写入。当前服务必须载入新代码后才能恢复页面上的建议，真实环境恢复仍需确认。错误关联、非法步骤或越权目录的旧报告仍会被拒绝；此次修复不保证所有历史 INVALID 结果都可应用。

本结论是独立静态审查通过，不是测试或运行验收通过；归档、本地提交和服务运行管理由主代理继续处理。
