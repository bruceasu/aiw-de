# FD-002 Planner 测试授权：Revision 6（已批准）

<!-- aiw-data: FD-002-test-authorization-r6.json -->

## 审核结论

测试脚本已进行静态审阅，范围聚焦于 FD-002，使用已有本地依赖、专用临时
SQLite 与 Chromium。当前结论为 **approved**。用户于 2026-10-05 明确要求
`$fd-workflow auto test and review for FD-001, FD-002`，批准本轮定向测试及独立
审查；这解决仓库默认为零的运行预算。批准不包含下载、外部访问或全仓测试。
此前 pending 记录是准备阶段，并不构成此前已经运行。

## 唯一拟授权命令

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录：`D:/03_projects/AI-tools/aiw-dev`。预计 **3–5 分钟**，异常超时及清理
可能增加耗时。初验仅执行一次，不自动重跑失败断言或启动失败的应用。

本记录绑定：

- 实现事件：`FD-002-000006-implementation-ready`。
- FD revision：`6`。
- FD 摘要：`7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。
- 独立 Tester：`fd002-tester-20261005-15d090a2`。
- Planner：`fd002-planner-20261005-96860f94`。

命令启动前核对最新事件、领取人、FD 内容摘要与本授权记录；pending、denied、
文件缺失或绑定不符均停止。若修改产品并产生新实现事件，须重新记录对应版本的
精确授权，不能复用本记录绕过版本校验。

## 范围与静态审阅

具体用例见 [独立测试计划](FD-002-test-plan-r6.md)。已审阅启动器、数据库夹具、
CDP 浏览器脚本与场景库存，包括版本校验、循环终止条件、进程所有权、临时路径
边界、请求限制、逐断言证据和清理逻辑。

场景库存共 **104** 项，本命令计划断言 **96** 项，计划覆盖上限为 **92.31%**；
这不是实际覆盖。当前执行及通过均为 **0**。验证 ADMIN、VIEWER、OPERATOR、
APPROVER，以及 390/768/1280/1440 像素页面：原始 requirement、真实依赖图、
200 个任务/1200 条依赖/300 个 attempts/300 条 Timeline 的范围和截断、状态与
时间、权限与控制操作、刷新保持、表格回退及截图。

调用代码限定为 `tests/fd002/runtime.py`、`fixture.py`、`browser.mjs` 与
`scenarios.json`。沿用已有编译类和本地 classpath，不运行 Maven 或编译。

## 副作用与运行限制

- 四个角色顺序运行，每个角色各创建自己的临时应用、SQLite、Chrome profile。
- 应用监听 `127.0.0.1` 随机端口，关闭 Telegram、自动审批和租约恢复；浏览器
  请求仅允许当前临时应用地址，阻止外部请求；不下载依赖、不访问外部服务。
- 只对专用临时数据库造数并执行控制操作，不读取或写入生产数据库。
- 证据写入 `tests/fd002/evidence-r6-<时间>-<PID>/`，保存数据库、日志、截图、
  脚本快照和逐断言结果。不会生成发行物。
- 只终止本轮创建的 Java PID 与 Chrome PID 进程树；只清理已校验归属的
  `%TEMP%/fd002-tester-*`。终止或证据保存失败则保留原路径并报告。

## 缺口和待决事项

8 项保持未覆盖：无 JavaScript 阅读、Firefox 图与布局、Firefox 刷新保持、
真实租约到期 requeue、pause 撤销真实 Worker 租约、真正同时的控制 POST、
非 UTC 浏览器时区、缺失可选 attempt started/finished 时间。
业务分支覆盖为 **unavailable**，没有字节码覆盖仪器。Timeline 输出上限也不
代表底层全量读取成本有界。

本轮提出将 FD-002 的 Firefox 检查延期，等待人类决定；不能套用 FD-001 的决定，
也不能把延期记录为通过。其余运行结果和覆盖例外须由 PM 据实际证据决策。

可供人类授权的重试范围：初验后仅在相关产品或脚本已经修正时，再运行同一条
定向命令一次；新事件须重新绑定版本记录，保留失败证据，不扩展范围、不重跑
未改动的失败命令。该范围按用户明确测试请求和核心运行预算批准；初验与相关
修正后复验各限一次，不再自动追加。

## 当前记录

审核时间：`2026-10-04T22:22:45+00:00`。
`human_approval: approved:conversation:20261005-auto-test-review-FD001-FD002`。
尚未执行本命令、浏览器、Python/Node 检查、运行测试或覆盖统计；尚无测试通过
结论。本记录批准上述精确命令一次，Firefox 延期仍待单独决定。
