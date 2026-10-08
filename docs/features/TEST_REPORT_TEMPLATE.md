# FD-XXX 独立测试报告，第 N 轮

<!-- aiw-data: FD-XXX-test-report-rN.json -->

## 结论

说明本轮实际运行了哪些测试、结果、需求场景覆盖率与业务代码分支覆盖率。
未运行、失败或无法测量的事项应明确标出，不得写成通过。

## 场景与证据

把宽泛验收项拆成独立的可观察场景，逐项列出测试用例、实际结果和未覆盖原因。
纯静态要求单独说明排除理由。JSON 中的 `scenarios` 数组是机器统计的依据，
其数量与通过数必须和摘要字段一致。
每个条目需要唯一 `id`、非空 `behavior` 和 `status`；`status` 仅可为
`passed`、`uncovered`、`blocked`。`passed` 还需要非空 `test_cases` 数组。

## 命令与风险

列出每次实际命令、授权记录、原始输出或证据路径、环境限制及剩余风险。
中文 Markdown 供人阅读；同名 JSON 按 `TEST_REPORT_DATA_TEMPLATE.json` 编写，
供 CLI 和 AI 消费。两份文件必须引用同一 FD、事件和版本。
