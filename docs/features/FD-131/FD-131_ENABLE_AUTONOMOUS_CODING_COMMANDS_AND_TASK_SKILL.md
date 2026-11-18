# FD-131: 通用 Coding Worker 自主执行

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Dual

## 目标

让现有 Coding Worker 在一次任务里编辑代码、执行项目的构建和测试、根据输出修复并重跑。首期使用可信执行账号，Sandbox 不作为前置条件。

## 方案

- 简单任务和工作流显式授予自主命令权限，默认关闭。任务同时启用工作区工具。工作流的授权适用于启用工作区工具的任务节点。计划调度和秘书委托继续遵循现有权限边界。
- Plane 冻结权限并校验每次命令的有效租约。固定命令继续走原入口。Windows 使用 PowerShell 和 Job Object，Linux 使用 sh 和进程组。两个 Runtime 的任务命令共用已有工具代理。
- 命令运行使用经过筛选的环境变量，输出设上限。文件和网络访问由执行账号及部署环境决定。Linux 进程组无法证明脱离该组的后代已退出，Worker 异常退出时的清理须在部署环境验证。
- 沿用可选 Skills、任务资料和现有版本冻结。用 Java S3 SDK 场景验证，没有真实云资源访问要求。

## Work items

- [x] 1. 接通工作区授权、命令执行和回执。
- [x] 2. 在 OpenAI Agents SDK 和 Codex App Server 的受控入口注册同一工具。
- [ ] 3. 用同一代码修复和取消场景实测两条路径。确认异常退出后工作区不会被残留进程修改。
- [x] 4. 接通工作流选定的冻结 Skill，并登记 `java-s3-sdk`。
- [ ] 5. 对照验证普通 Java 和 Java S3 任务的资料装载。
- [ ] 6. 独立审查授权及取消路径。

## 验收

1. 普通 Java 任务能够看到测试失败，修复后重跑并记录命令、结果和改动。
2. 默认关闭自主命令，未授予权限或租约失效时拒绝。取消须核对进程清理结果，无法确认时保留现场。
3. 普通任务不加载 AWS 资料。S3 任务能使用固定的专业资料，并在没有真实云账号凭证的环境完成验证。

## Verification

- 通过 JDK 25 离线 Maven `-DskipTests compile`。修改过的 Python 源码通过 `compile()`，`workflows.js` 通过 `node --check`，`git diff --check` 无错误。
- 用一份临时 Java 项目副本准备一个有失败断言的小缺陷。分别在两种 Runtime 勾选工作区工具和自主命令，要求先复现失败再修复重跑。核对命令、退出状态和交付差异。在另一次任务用可观察的子进程验证取消和异常退出。
- 在另一个临时项目上选择 `java-s3-sdk` 并附上项目版本对应的 API 资料，要求使用现有 mock 验证请求构造。对照普通 Java 任务的冻结 Skill 清单。
- 上述运行场景尚未执行，尚无独立审查结论。

## Sources

- [需求稿](../../research/AIW_General_Coding_Agent_Requirements.md)
- [开发说明书](../../research/AIW_General_Coding_Agent_Capability_Enhancement_Spec.md)
