# FD-001 Worker 评审修正交接 r10

<!-- aiw-data: FD-001-implementation-repair-r10.json -->

**Worker session:** fd001-worker-20261004-b2aa6c85

**Source event:** FD-001-000010-changes-requested

**审查基线：** ae19d463436fe9730b07e7106ecbdfacfab98845；首轮独立 [评审报告](../reviews/FD-001-review-r9.md)，失败累计 1/3。

## 修正与原因

| 发现 | 最小修正 | 静态证据 |
|---|---|---|
| F1 分类总量/样例集合不同 | 私有 QueryService 的五组样例对齐 Fleet 原有互斥优先级；额外未满足条件在该任务内分别显示；两份指南和 FD 说明同步 | policy → Run admission → capability → Worker capacity → Fleet admission 与原 repository 分支一致，未改 Fleet 统计/调度/公开契约 |
| F2 动态数量改变展开 key | waiting-group 用类别 label 设置 data-detail-key | key 不含 total、任务列表或快照时间 |
| F3 跨区域选区破坏 | 非折叠 Selection 的所有 Range 与 region 判断 intersectsNode，保留相交的起点/终点/中间区域 | 沿用原 focus、陈旧提示、单请求、10s timeout 和刷新周期，未扩展生命周期 |

本阶段仅改变私有展示谓词、模板、浏览器阅读保护和附近说明；没有依赖、schema、权限、公开协议、持久化或 Worker 行为变化。

## 实际验证

JDK 21 下运行一次 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile`：175 个源文件、BUILD SUCCESS、4.079 秒。仅更新 classes/resources，未跑测试或打包，日志保存于 docs/features/reports/FD-001-visual-evidence/repair-compile-r10.txt。没有重复编译。

静态读取权威分类和相邻代码，检查 scoped diff；Tester 脚本、数据库 fixture 修改与 CDP 副作用已审阅。静态发现并修正测试 fixture 的 run00 admission 饱和问题（新任务改用 unlimited 的 run08）；选区断言增加未选中区域确实刷新替换的观察，避免“根本没刷新”也通过。

## 独立复验交接

独立 Tester 已准备 `C:/green/python312/python.exe tests/fd001/runtime.py review-fixes`，只运行 ADMIN 临时应用，预计 20-40 秒；4 个业务断言覆盖分类样例/零计数、policy+unmatched 主分类、计数变化的展开保持、跨区域选择与中间节点保护。无全矩阵、下载、外部服务或生产 DB。

该命令尚未运行。r7 两条授权已使用完，下一运行须用户新增单次预算，并绑定真实新实施事件/revision/digest/Tester session。原 36/36、42/88 仅作历史基线，不回写或冒称在修正后重跑。Firefox 延期、分支/规模成本未测及未覆盖场景继续保留。后续 PM 和独立 Reviewer 必须依据实际新证据判断，当前不完成或归档。

