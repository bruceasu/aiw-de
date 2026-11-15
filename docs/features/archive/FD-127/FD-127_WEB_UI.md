# FD-127: 桌面 Pet 启动入口

**Status:** Complete
**Revision:** 4
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Dual

## Problem

单独生成一个打开浏览器的快捷方式没有提供桌面 Pet 入口。现有秘书 Pet 已能打开对话和工作台，本 FD 应让用户从桌面快捷方式启动并看到 Pet。

## Decision

为当前 Windows 用户生成桌面快捷方式，调用现有 `start-desktop.ps1 -ShowPet`。点击 Pet 打开秘书对话，右键菜单可进入工作台。沿用现有 Pet 界面及个人服务连接行为。

## Scope

- 提供可重复运行的 PowerShell 脚本，在当前用户桌面创建或更新同一个快捷方式。
- 不安装 Windows 服务、不注册系统登录自启动、不安装依赖。
- 不增加只打开浏览器的快捷方式，不更换 Pet 或工作台 UI，不引入 Electron/Tauri。
- 若 Pet 实例已运行，沿用现有单实例和托盘行为；快捷方式不再启动第二个 Pet。

## Work items

- [x] 1.1 核对现有 Pet 的启动脚本、点击打开对话及菜单打开工作台的行为。
- [x] 1.2 添加生成/更新 Windows 桌面快捷方式的脚本，启动时显示 Pet。
- [x] 1.3 补充创建、使用及现有服务行为说明，并做静态检查和 PowerShell 语法编译检查。

## Acceptance

- 运行快捷方式生成脚本后，当前用户桌面出现秘书 Pet 快捷方式；重复运行更新同一个快捷方式。
- 打开快捷方式时，若没有已运行的 Pet 实例，则通过现有启动脚本显示 Pet；已有实例时不创建重复实例。
- 点击 Pet 打开秘书对话，右键菜单可打开工作台。
- 仍沿用 Pet 现有的本地服务连接/启动行为；不安装服务或设置系统登录自启动。
- Pet UI 依赖缺失时保留现有提示，不自动下载或安装依赖。

## Verification

- PowerShell `Parser.ParseFile` 解析新增脚本：通过；`git diff --cached --check`：通过。
- 静态检查表明快捷方式调用现有 `start-desktop.ps1 -ShowPet`，重复运行更新同一个 `.lnk`，已运行 Pet 时遵循既有单实例/托盘行为。
- 未运行测试、创建真实桌面快捷方式、启动 GUI 或个人服务；Windows 桌面实际交互仍未验证。

## Sources

- 用户澄清：桌面 Pet 应作为打开秘书入口的方式。
- 静态代码检查：现有 Pet 点击打开对话，右键菜单提供工作台；启动脚本已有 `-ShowPet`。

**Completed:** 2026-11-15
**Disposition reason:** 桌面 Pet 启动快捷方式已实现；PowerShell 语法解析通过，桌面运行留待本机实际使用确认。
