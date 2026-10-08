# 测试脚本版本记录

attempt1/attempt2 的脚本是根据本 Tester 在实际执行前的 apply_patch 修改记录与当前脚本恢复的历史版本，不是在执行时自动捕获的不可变快照；没有改写历史测试输出，也不宣称原始脚本散列已在执行时留存。attempt1 使用 fetch 登录且 seed 未显式 close；attempt2 使用原生登录表单、导航 URL/readyState 检查、显式 SQLite close 和跳转上下文瞬态处理。

随后 runtime.py 新增 diagnose 分支（只 ADMIN、无 seed、HTTP GET 安全摘录），新 diagnose.mjs 创建独立 page target 并记录网络/导航；该诊断还未运行，不属于 attempt2。当前 browser.mjs 的新 target 修改是待验证的基础设施假设，不代表根因已解决。

两轮均零业务断言执行；原输出、导航日志及报告保持原结果。
