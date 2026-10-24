# FD-039 Worker 实现 r1

<!-- aiw-data: FD-039-implementation-r1.json -->

Worker secretary-desktop-20261024-a6529740；来源 FD-039-000003-design-ready。比较09e2485..aaa172c，源码500080d、8d48126、aaa172c，主工作区范围提交；本轮失败审阅0次。

## 实现

用户要求原型属于可配置Skill，而非独立Worker。本次新增prototype辅助技能，完整入口与UI/逻辑两份方法：从任务问题选择分支，实际制作最小可运行文件/交互，交付入口、重置/退出和观察结论。UI包含结构不同变体、模拟交互、现有视觉风格与预览方法；逻辑包含纯规则/状态、文本交互、正常/边界/非法动作与预期结果。

primarySkill、工作方法、运行后端、角色与原REPORT/VERDICT/WORKSPACE不变。只有任务允许时激活；写文件、命令、预览、浏览器、服务、真实数据和安装都沿用原权限，不能从Skill推导授权。无执行权限可交付文件和未运行说明；缺写权限报告缺口。原型不自动成为生产实现，不自动创建分支、合并或删除其它方案。

## 装配与兼容

software-catalog.json只在原minified结构的assets数组追加prototype-v1正文/摘要，旧9资产、11方法、其它协议元数据保持。index.files加入3个文件，不修改profiles映射。现有ConfigurationUiOptions已输出所有公共Skill；configuration-methods.js会把勾选资产放入additionalSkills；ExecutionConfigurations.propose把它加入协议Skill引用，再由content校验及SkillFiles.configured冻结完整包。无需新的Worker/角色/进程、Java/UI代码或Schema。

平台三个资源同步为三语言九个同名副本。既有已创建/准备任务保留冻结字节；新组合方法可采用prototype。动态配置仍由当前已支持的Worker/后端消费，不因资源副本而宣称其它语言实现新协议。说明见WORKER-PROTOTYPE-SKILL.md及现有配置说明；来源矩阵更新FD038方法吸收与FD039完整技能的区别，稳定规格明确组合语义。

## 实际检查与限制

一次范围git diff --check及内联只读检查通过：新资产摘要与三份声明资源合成正文一致；全部10资产文本摘要有效；旧9资产/11方法和角色映射不变；新增链接存在且在清单内；三语言9副本与规范源字节一致，资源大小合规，宿主.agents/skills未改；原additionalSkills接线静态可追踪。没有导入或执行生产模块或技能脚本。

C:/green/bin/setup-jdk25.ps1（JDK25.0.1/Maven3.9.4）后执行mvn -o -Dmaven.test.skip=true compile通过，结束2026-10-24T13:44:12+09:00；131应用资源及2配置资源复制，Java up to date。环境恢复，脚本打印package/run示例未执行。其它工作区Java不属于本FD审阅。

未运行工厂构造器/真实目录选择、浏览器/UI、模型/Worker/实际原型/预览、SDK/工具/恢复、测试、数据库/服务/网络/依赖下载、最终构建/打包、安装、重启或发布。静态和资源复制不是运行通过或原型质量证据。并行秘书/模型/Python/Java/模板未暂存，未修改宿主原型技能。无未解决blocker；下一步独立Reviewer审实际技能、装配兼容、权限与输出后host正常范围归档。
