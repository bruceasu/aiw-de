# FD-023 第2轮独立实现评审

<!-- aiw-data: FD-023-review-r2.json -->

结论：**changes-requested**。R1的旧地址已全部替换，但现行文档的认证路径分类尚未同步，不能仅凭旧路径扫描零残留判定文档完成。

- Reviewer：`fd023-reviewer-20261021-01`；已核对最新receipt并claim `FD-023-000006-implementation-ready`。
- 当前HEAD：`901afda`；修复差异：`5f454d5..901afda`，原整体审查基线 `6f34bf4`。FD Revision 6；本轮为第2次实现outcome。

## R1剩余项：迁移后的认证分组说明错误（中等，阻断）

FD Work 2.2及“当前文档同步”“六个console API根保留原人工边界”验收尚未完整满足：

- 中英文 `docs/current/16-CONTROL-PLANE-API.md:20`、`docs/current-zh/16-CONTROL-PLANE-API.md:20` 将原来不同旧前缀机械替换为重复的 `/api/r1/**`，随后声明该组没有额外认证。该范围如今包含必须使用session/CSRF的console API及机器认证API，分类扩大而语义没有收窄。
- 两份16手册`:21`仍将JSON管理/输出API放在 `/ui/**`，`:218`仍将这些API的CSRF条件限定为 `/ui/**` 写请求。实际这些数据API已迁到六个 `/api/r1` 根。
- `docs/current/15-WEB-CONSOLE.md:83` 和 `docs/current-zh/QUICK-START.md:232` 同样仍将控制台认证范围表述为仅 `/ui/**`，未包含迁出的数据API。

后果：读者按错误分组选择凭据或遗漏CSRF，新console数据访问失败，认证边界说明与代码及同页canonical导言相互矛盾。最小修正：两语言16手册明确列出六个console API根及session/CSRF/role/owner边界，另列机器lease/bearer/delegate规则和其余legacy管理写入首次代理启用后持久人工认证的条件；Drain/Offline仍是管理写入。同步上述15/QUICK-START认证范围描述。

两语言09安全说明仍描述旧管理信任边界：英文`:9`、`:44–61`，中文`:24–39`。没有Spring Security或完整生产edge框架这一点本身属实，但同步上述分类时应补充实际自定义interceptor和代理首次激活后的持久认证条件，避免继续暗示所有管理API都无应用认证。保留部署网络加固建议和读接口原有策略，不扩展实现范围。

## 本轮已确认

10份英文现行文档的具体API URL已经同步；canonical、Worker base、外部客户端同步升级和无alias说明存在。`rg --pcre2`扫描英文current没有旧API/UI JSON根残留。修复差异仅文档；`git diff --name-only ea00861..901afda -- src workers tests scripts openspec`无源变更，因此第1轮对路由、六console根、安全顺序、机器白名单、context相对分类及当前调用方的静态结论继续适用。

r2 Worker证据明确纠正初次遗漏英文目录的扫描范围，没有将历史报告改写为已完整检查。未发现新的源代码问题。FD的2.2仍附“尚未同步”旧注且新Verification一行出现字面问号，需在修复状态记录时清理；不作为独立实现缺陷。

## 实际操作与未验证

执行了 `aiw fd show/claim`，`git status --short`、`git diff --stat`、定向文档diff和上述源路径name-only检查，`Get-Content`、`rg`定向认证语义与旧根扫描。没有执行测试、编译、typecheck、服务、HTTP、浏览器、网络、迁移、构建或子代理，也未尝试被自动审批拒绝的Java示例编译/清理。生产Java261文件及多语言编译仍是原Worker证据；文档修复不需重跑。

真实Spring路由/认证交互、非空context path端到端及外部客户端切换仍未实测。第1轮生产静态支持未升级为运行时通过。修正文档和证据后可进入本循环第3次独立评审。
