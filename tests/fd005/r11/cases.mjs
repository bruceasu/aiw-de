import { roles, scenarios as priorScenarios, staticExclusions } from '../r8/cases.mjs';
export { roles, staticExclusions };
// Independently checked and claimed by this Tester; binds only implementation-ready event 12.
export const binding = Object.freeze({
  fdId: 'FD-005', event: 'FD-005-000012-implementation-ready', revision: 12,
  digest: 'bf3c8b3e65091a9100ffe90442316b6875384228b03a894c9a7fb014e6f353c4', session: 'fd005-tester-20261005-7865d383',
});
const entries = priorScenarios.map(item => ({ ...item }));
const add = (id, behavior, acceptance, plane, prepared = true) => entries.push({ id, behavior, acceptance, plane, prepared });
add('TOOL-fatal-error-stop', '未分类的致命普通 Error 取消后续工具，且不声称可修正或成功', [3,8], 'public-core');
add('SDK-summary-call-pairs', '实际 SDK 压缩达到摘要目标并在后续请求保持完整工具调用与结果对', [2,7], 'actual-api-offline');
add('SDK-summary-parent-cancel', '实际摘要 fetch 已开始后继承 attempt 取消，停止新增请求和成功上报', [3,7,8], 'actual-api-offline');
add('API-adapter-settled-bound', 'adapter settled 未结束时宿主在清理宽限内返回资源未知并保留工作区/内存', [3,8], 'public-attempt-offline');
add('API-active-io-bound', '活动 I/O 不响应取消时有界收尾，不清理或释放仍使用的工作区', [2,3,8], 'public-attempt-offline');
add('LOG-success-consistency', '成功执行、accepted 上报、资源终态与持久标记互相一致', [3,8,10], 'actual-api-offline');
add('LOG-complete-unknown-consistency', 'complete 回执未知时报告未知且不把已结束资源误写成未知/未结束', [3,8,10], 'actual-api-offline');
add('LOG-resource-unknown-consistency', '资源未结束时终态显式未知且无成功/accepted/ready-to-report误报', [3,8,10], 'public-attempt-offline');
add('SLOT-resource-unknown-stop', '公共 slot 获资源未知后停止该 slot 新领取并通知宿主停止其他领取', [3,11], 'public-slot-offline');
add('SDK-edit-many-strict-nested', '实际 SDK edit_many 拒绝嵌套未知字段且无 complete/文件副作用', [2,8], 'actual-api-offline');
add('SDK-tool-parameter-type', '实际 SDK write 拒绝错误参数类型且无 complete/文件副作用', [2,8], 'actual-api-offline');
add('SDK-tool-parameter-json', '实际 SDK 工具参数 JSON 无法完整解析时无 complete/文件副作用', [2,5,8], 'actual-api-offline');
add("SLOT-outcome-observer-throws","资源未知后 outcome 观察回调抛错仍不恢复 slot 领取",[3,11],"public-slot-offline");
add("SLOT-uncertain-observer-throws","资源未知后宿主通知回调抛错仍永久退役 slot",[3,11],"public-slot-offline");
add("SLOT-uncertain-observer-timeout","宿主资源未知通知未返回时只等待清理窗口并永久退役",[3,11],"public-slot-offline");
add("SLOT-drain-inflight","公共 slot drain 停止新领取并允许已开始事务完整结束",[3,11],"public-slot-offline");
add("SLOT-force-before-start","force 发生在 claim 等待中时不启动随后送达的任务",[3,11],"public-slot-offline");
add("SLOT-shared-host-stop","一个 slot 资源未知后公共宿主通知使共享停止/force阻止另一 slot 启动新任务",[3,11],"public-slot-offline");
add("RUNNER-stage-record-bound","阶段元数据 I/O 未结束时整体期限与宽限生效、业务回调不启动且资源未知",[3,8],"public-core");
add('API-uncertain-termination-bound', '明确 RESOURCE_TERMINATION_UNCERTAIN 后协调器永久保留租约并拒绝后续事务，不将普通取消 I/O 混作此状态', [2,3,8], 'public-attempt-offline');
export const scenarios = Object.freeze(entries.map(item => item.id === 'REPORT-manifest' ? {
  ...item, verificationScope: 'partial',
  uncoveredReason: '只验证实际 adapter report 字段；完整 manifest 与线上审批消费未执行，WorkspaceResult 另按实际 Git 结果记录，不能从局部通过推成完整兼容。',
} : item));
export const byId = new Map(scenarios.map(item => [item.id, item]));
