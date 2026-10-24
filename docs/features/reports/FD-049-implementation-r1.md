# FD-049 实现报告 r1

<!-- aiw-data: FD-049-implementation-r1.json -->

Worker Session：desktop-expression-20261024-a19d05。来源 FD-049-000003-design-ready。基线 b40af38。

## 实现

新增 desktop_pet.py：pet_state 纯投影与 PetAnimator。沿用 Tk Canvas，原插画轻微起伏，完成时加强起伏；独立矢量表情气泡具眨眼、微笑、思考、疑问、担心、休息及对应中文状态，不修改插画面部，不声称多姿态/Live2D/真实口型。没有新增依赖或生成图片。

状态来自成功快照，日常暂停用 assistant.paused，不使用专业平台 enabled。未知结果覆盖旧成功状态。实际 work DAILY_RUNNING/ACTIVE 表示工作中，消息 PENDING 只表示等待回复，SCHEDULED/START_PENDING 只表示等待执行。待设置/确认/阻塞/暂停优先，不把单次请求成功当作任务完成。仅已观察任务非 DONE→DONE 或消息 PENDING→DONE 触发 6 秒完成/新回复提示；初次历史读取、错误后重连不推断完成事件。

desktop.py 接入 image item、快照、请求开始/结束、错误、隐藏/显示/退出及“人物动画”右键开关。后台轮询标记 background，不伪装工作请求；隐藏取消 after，显示只恢复一个回调，退出先取消。无新增线程、服务调用或调度行为。

## 范围提交与实际检查

本轮开始 desktop.py、README、Runtime 等有其他工作未提交修改。本 FD 不改脏 README，以 desktop-assets/README.md 记录实际能力。工作区 desktop.py 上下文差异导致一次 git apply --cached 未成功，未改变 index；随后由 HEAD 逐条精确应用本次 15 处动画接线并用 Git blob 更新 index，完整保留工作文件中的其他修改。最终暂存 desktop.py 差异只包含本 FD；未暂存整文件或其他任务文件。

git diff --cached --check 通过；同一静态批次读取暂存差异及统计。Python 标准库 compile() 在内存编译工作区 desktop.py、desktop_pet.py，以及提交区 desktop.py，exit 0，无产品导入、Tk 启动或 pyc。已知秘书 compile 脚本包含无关模块，本次采用最小 Python 编译。

## 验收与限制

1. Canvas 图像位移和气泡绘制对应动画；中文说明与纯状态投影对应八类真实状态。
2. update_snapshot 的前态比较与 uncertain/connected 门控覆盖历史记录、响应成功、重连与短时反馈；后台 poll 使用 background=True。
3. assistant.paused 与错误覆盖分支纠正旧显示。
4. set_enabled、set_visible、close 与单 timer ID 处理生命周期，拖动/托盘原入口保持。
5. 仅三份产品文件及本 FD 证据提交；无新依赖。

未运行测试、GUI、模型、服务、网络、重启、部署或打包。实际 Windows 外观、帧率/CPU、表情辨识、托盘恢复和关闭行为未实测。当前实现是人物起伏与表情气泡，不是人物面部变形或多帧角色动作。
