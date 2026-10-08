# FD-002 Planner 测试授权：r12 v1（人类批准）

<!-- aiw-data: FD-002-test-authorization-r12-v1.json -->

## 决定

**approved / human-approved**，新增一次初验及一次仅相关修正后的同命令重试。
人类批准：`approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE`；
此为绑定实际 event12/revision12 的新授权，不是延长已用完的 r9 预算。
用户上次批准的一次复验及一次相关修正重试均已使用：r9 最新实际结果为
78 项执行、74 通过、4 失败、26 未运行（71.15%），经 PM 正式拒绝。
原五轮失败和脚本快照全部保留。r12 尚无新执行结果。

## 命令与修正范围

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；预计 4–8 分钟，包含完整长页分段，受本地内存和截图成本影响。
产品已将 Timeline 条目标记改为静态 HTML 属性；离线 compile-only 成功，
0.802 秒。测试准备包括严格完整 attempt ID 集合、真实条目/祖先可见性、
类别 value/label 诊断，Node 内存空间限制，以及保留全部纵向内容的有界分段截图。
最终实际脚本已由宿主完成一次定向 diff/SHA256 静态审阅：完整 300 个 ID 集合比较、
可见语义 Category 控件、祖先与布局可见性、筛选前后集合、逐段截图持久化均已检查；
Node 参数在脚本路径前，原四角色、四宽度、超时、夹具及 104/96 库存保持。
绑定实际 event12/revision12/digest/session 和本份正式授权；批准前未运行测试、浏览器或语法检查。
准确脚本摘要见同名 JSON；[独立 Tester 提案](FD-002-test-proposal-r12-v1.md)已完成。
这些静态准备与编译结果不代表验收通过。

用户已明确批准一次复验，以及仅在发生相关修正后最多一次同命令重试；
每次均须独立 Tester 执行、保留真实结果，条件重试还需宿主静态审阅相关修正并明确通知。
当前 grant 不沿用 r9 的已使用预算。AGENTS.md/core validation 与 fd-workflow
要求新增运行单独批准并绑定当前事件；此批准不包含其他模式、诊断或扩大验证范围。

## 真实事件绑定

`FD-002-000012-implementation-ready`；revision 12；digest
`8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9`；
Tester `fd002-tester-20261005-15d090a2`。启动器须核对该最新事件的领取人、
FD 正规化摘要、版本、批准文件、精确命令和 cwd，任一不匹配先退出。

## 副作用及风险

104 项库存，计划 96 项，四角色和 Chromium 四宽度；92.31% 仅是计划上限。
Firefox 的两项已单独延期，原 8 个缺口留在库存分母；分支覆盖未测。
只启动自有 loopback 应用、Chromium、临时 SQLite/profile，保存新
`tests/fd002/evidence-r12-*` 下的快照、诊断、分段截图和结果。
清理只限自有 PID 与已核对的 TEMP 直属路径；无下载、外部服务、生产数据写入
或最终构建。系统提交空间不足已实际造成 JVM/Node 中断，新的资源设置和
分段截图尚未复验，也不能验证生产性能。原 Timeline 全量读取成本未测。

Planner `fd002-planner-20261005-96860f94`；`human_approval: approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE`。
