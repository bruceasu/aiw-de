# FD-XXX Planner 测试授权，第 N 轮

<!-- aiw-data: FD-XXX-test-authorization-rN.json -->

## 审核结论

用中文说明精确命令、工作目录、范围、预计时长、调用代码、文件副作用和风险。
只有离线、聚焦、可检查且仅影响指定目录或临时文件的命令，Planner 才能
直接批准。危险或无法判断的操作必须先获得人类明确批准。

同名 JSON 按 `TEST_AUTHORIZATION_DATA_TEMPLATE.json` 编写。人类批准时，
`human_approval` 使用 `approved:<source>:<id>` 引用；`denied`、`pending`
或没有答复均不构成授权。每条记录只批准当前 FD 版本的一条精确命令。
