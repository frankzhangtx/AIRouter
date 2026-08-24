# OpenCode Android 自动编程编排插件：开发与发布待办任务

## 任务信息

| 字段 | 内容 |
| --- | --- |
| 任务 ID | `TASK-OPENCODE-ANDROID-ORCHESTRATOR-PLUGIN-001` |
| 状态 | `IN_PROGRESS`，完整 V3 资源模板迁移已完成，动态适配与安装生命周期尚未实现 |
| 创建日期 | 2026-08-19 |
| 最近核对日期 | 2026-08-24 |
| 任务类型 | OpenCode 插件开发、Android 工程初始化器、双版本兼容与 npm 发布 |
| 当前本机 OpenCode | `1.15.13` |
| 目标电脑 OpenCode | `1.14.22` |
| 当前方案基线 | OpenCode coding orchestration V3，Git HEAD `829693652e3737ad94c7cc75214b09fb2b58715b` |
| 插件源码 | `/Users/zhanglong/files/npmprogram/opencode_android_orchestrator` |
| 插件开发基线 | Git `7fcc371b05739af7bc19c671f703a406fc84c5c9` |
| 正式包名 | `@frankzhang2026/opencode-android-orchestrator` |
| 已发布版本 | `0.1.0`，2026-08-20 发布的早期空壳包，不具备可用安装器 |
| 下一开发版本 | `0.2.0`，仅本地开发，尚未批准或执行发布 |
| 回滚基线 | `方案备份/` |

> 本任务已进入开发阶段并完成基础能力提交。当前工程切换和 `0.2.0` 正式发布
> 仍是独立授权关口，未经后续明确确认不得执行。Registry 中已有的 `0.1.0`
> 只是历史空壳版本，不代表本方案已满足发布或验收条件。

## 一、任务目标

将本工程已经验证的 OpenCode Android 自动编程方案封装成可复用插件包，
让其他 macOS Android 工程通过一条初始化命令完成安装，不再手工复制
Agent、Command、Skill、Shell 脚本、Schema 或配置模板。

计划中的使用方式为：

```bash
npx @frankzhang2026/opencode-android-orchestrator@0.2.0 init .
opencode --agent scheduled-planner .
```

`0.2.0` 是当前开发目标版本，尚未发布；以上命令只能在完成安装生命周期、
双版本验证和发布授权后作为正式使用方式。

## 二、必须满足的结果

1. 同一插件版本支持 OpenCode `1.14.22` 和 `1.15.13`。
2. 以 `@opencode-ai/plugin@1.14.22` 作为最低编译基线，只使用两版公共 API。
3. `init .` 自动安装所有必要的工程内资源，不要求用户手工复制 Shell 脚本。
4. 自动识别 Git 根目录、Gradle Wrapper、Android 模块及 Kotlin/Groovy DSL。
5. 不复制 Android 产品工程，不产生完整工程副本或重复 Gradle 构建目录。
6. 保留当前 V3 的三次 OpenCode 单选确认交互。
7. 保留计划、合同、代码和测试只生成一个正常路径提交的策略。
8. 保留原分支漂移阻断、任务分支安全删除、no-push 和无 launchd 注册约束。
9. 安装、升级和卸载均有文件清单、哈希、冲突保护及恢复方法。
10. 发布前在两个 OpenCode 版本上通过相同的真实集成测试。

## 三、推荐实现形态

采用“npm OpenCode 插件 + 一键初始化 CLI + 工程内可审计资源”的混合结构，
第一版不将已验证的确定性 Shell 事务逻辑重写为 TypeScript。

```text
opencode-android-orchestrator/
├── dist/
│   ├── index.js                 # OpenCode 插件入口
│   └── cli.js                   # init/doctor/upgrade/uninstall
├── src/
│   ├── plugin/                  # 两版共有的插件钩子
│   ├── compatibility/           # OpenCode 版本检查与兼容层
│   ├── installer/               # Android 检测、复制、合并和清单管理
│   └── tools/                   # 首版只读诊断工具，后续可增加脚本包装工具
├── templates/
│   ├── .opencode/agents/
│   ├── .opencode/commands/
│   ├── .opencode/skills/
│   ├── automation/
│   ├── scripts/automation/
│   └── docs/plans/
├── tests/
│   ├── unit/
│   ├── fixtures/android-kts/
│   ├── fixtures/android-groovy/
│   └── compatibility/
├── package.json
├── README.md
├── CHANGELOG.md
└── LICENSE
```

## 四、现有资源的迁移方式

| 当前资源 | 插件化处理 |
| --- | --- |
| `.opencode/agents/scheduled-*.md` | 放入插件模板，由 `init` 安装 |
| `.opencode/commands/*.md` | 放入插件模板，由 `init` 安装 |
| `.opencode/skills/scheduled-quality-*` | 放入插件模板，安装到标准 Skill 目录 |
| `scripts/automation/*.sh` | 保持现有实现，由 `init` 自动复制并保留可执行位 |
| `automation/config.json` | 转为 Android 工程自适应模板 |
| 两个 JSON Schema | 原样打包并安装 |
| `TASK-TEMPLATE.json.example` | 原样打包并安装 |
| 历史任务合同和计划 | 不作为新工程模板安装 |
| `.git/automation-runtime/` | 不迁移，仅属于原仓库审计数据 |
| `taskdescriptions/*.html` | 作为开发参考，不作为插件运行依赖 |
| `AGENTS.md` | 只合并带标记的必要片段，禁止覆盖目标工程原文件 |
| `opencode.json` | 结构化合并插件引用，保留目标工程已有配置 |

## 五、一键初始化器职责

`init .` 必须自动完成：

- 检查目标目录是否为 Git Android 工程。
- 检查 OpenCode 版本是否满足兼容要求。
- 检查 `git`、`jq`、`rg`、`shasum`、Java、Gradle Wrapper 和 Android SDK。
- 识别 `settings.gradle`/`settings.gradle.kts` 及 Android 模块。
- 在修改前备份所有可能受影响的少量配置文件。
- 安装 Agent、Command、Skill、Shell 脚本、Schema、配置和模板。
- 对 Shell 脚本恢复可执行权限。
- 合并而不是覆盖 `opencode.json`。
- 固定插件版本，不使用浮动 `latest`。
- 生成 `.automation-plugin/manifest.json`，记录版本、来源和文件 SHA-256。
- 对已有同名但内容不同的文件停止并报告冲突，禁止静默覆盖。
- 自动运行 `doctor`、基础发现检查、自动化测试和 shadow run。
- 输出安装结果、警告、下一步启动命令和回滚入口。

## 六、双版本兼容约束

插件只允许使用 1.14.22 与 1.15.13 共有的能力：

- `PluginInput` 中的 `directory`、`worktree`、`client` 和 `$`；
- `config`；
- `tool`；
- `command.execute.before`；
- `permission.ask`；
- `shell.env`；
- `tool.execute.before` 和 `tool.execute.after`。

第一版禁止依赖：

- 仅在 1.15.13 提供的 `dispose`；
- `experimental_workspace` 及其两版类型命名差异；
- 动态 `skills.paths` 注入；
- 实验性聊天 transform；
- Auth 扩展；
- 未在 1.14.22 实际运行验证的 API。

兼容声明建议：

```json
{
  "peerDependencies": {
    "@opencode-ai/plugin": ">=1.14.22 <1.16.0"
  },
  "devDependencies": {
    "@opencode-ai/plugin": "1.14.22"
  }
}
```

包内额外记录“已认证版本”：`1.14.22`、`1.15.13`。低于最低版本时
`init` 必须拒绝；处于声明范围但未经认证的版本只允许在明确警告后继续。

## 七、外部依赖策略

### Superpowers

第一版继续固定使用：

```text
superpowers@git+https://github.com/obra/superpowers.git#v6.2.0
```

由初始化器自动加入配置并检查以下 Skill：

- `brainstorming`
- `writing-plans`
- `test-driven-development`
- `systematic-debugging`
- `verification-before-completion`

是否将这些第三方 Skill 打包进主插件，需要单独完成许可证和升级策略评审；
不作为第一版默认方案。

### opencode-scheduler

当前 V3 正常流程不是定时任务流程，新插件不应依赖或注册
`opencode-scheduler`，也不得创建 macOS launchd 任务。迁移验证通过后，
再单独确认是否从本工程 `opencode.json` 移除旧 Scheduler 引用。

## 八、功能开发待办

- [x] 确定正式 npm 包名、scope、仓库和许可证。
- [x] 建立独立插件源码目录或独立 Git 仓库。
- [x] 建立以 OpenCode 1.14.22 为基线的 TypeScript 工程。
- [x] 实现插件入口和公共 API 兼容层。
- [x] 实现 OpenCode 版本检测与基础 `doctor`（OpenCode、Git、Android、Gradle Wrapper）。
- [x] 实现 Android/Gradle 工程检测。
- [x] 将当前 V3 Agent、Command、Skill、配置、Schema、任务示例、计划说明和 AGENTS 片段转为安装模板。
- [x] 将 28 个自动化 Shell 文件转为安装模板并保持文件权限。
- [ ] 将配置中的工程名称、模块名和绝对路径改为动态生成。
- [x] 实现安全的 `opencode.json` JSON/JSONC 合并。
- [ ] 实现安装前备份和安装 manifest。
- [ ] 实现文件冲突检测，默认不覆盖用户修改。
- [ ] 实现 `init`。
- [ ] 完善安装态 `doctor`（依赖、资源、权限、配置和 manifest）。
- [ ] 实现 `upgrade`，仅升级未被用户修改的受管文件。
- [ ] 实现 `uninstall`，只移除哈希仍匹配的受管文件。
- [ ] 为只读 `status/doctor` 提供插件自定义工具。
- [ ] 评估第二阶段是否将高层脚本入口包装成类型化工具。
- [ ] 编写 README、迁移指南、故障排查和安全说明。

## 九、测试待办

> 2026-08-24 基线验证：`npm run typecheck` 通过，`npm test` 为 37/37
> 通过，`npm run pack:check` 通过且预览包含 92 个文件。当前测试覆盖基础检测、
> 只读合并规划、模板库存、源文件 SHA-256、权限及 AGENTS 受管片段边界，
> 尚不等于安装生命周期、双版本实机兼容或发布验收通过。

### 包和初始化器

- [ ] TypeScript 编译与 lint。
- [ ] `npm pack` 内容检查，禁止包含密钥、运行证据和历史任务数据。
- [ ] Kotlin DSL Android fixture 初始化测试。
- [ ] Groovy DSL Android fixture 初始化测试。
- [x] 多模块 Android 工程检测测试。
- [x] 已存在配置的无损合并测试。
- [ ] 同名文件冲突和无静默覆盖测试。
- [ ] 重复执行 `init` 的幂等性测试。
- [ ] `upgrade` 保留用户修改测试。
- [ ] `uninstall` 安全删除和保留修改文件测试。
- [ ] manifest 与 SHA-256 完整性测试。

### OpenCode 兼容矩阵

以下测试必须分别在 `1.14.22` 和 `1.15.13` 执行：

- [ ] 插件能够启动且无 API 错误。
- [ ] `opencode debug config` 正常。
- [ ] `scheduled-planner`、`scheduled-coder`、`scheduled-reviewer` 均可发现。
- [ ] `/change`、`/acceptance`、`/resume-review`、`/abort-task` 均可发现。
- [ ] 三个本地 Skill 和五个 Superpowers Skill 均可发现。
- [ ] 三个 OpenCode 单选确认正常，直接聊天文本不能批准。
- [ ] `./scripts/automation/preflight.sh --source` 通过。
- [ ] `./scripts/automation/tests/run-tests.sh` 为 `1..38` 全通过。
- [ ] `./scripts/automation/shadow-run.sh` 报告 `mutationPerformed: false`。
- [ ] 在临时 Android 工程完成一项真实的小型任务闭环。
- [ ] 最终只有一个包含计划、合同、代码和测试的组合提交。
- [ ] 原分支漂移能够阻断集成。
- [ ] 成功集成后任务本地分支被删除。
- [ ] 没有 Git push。
- [ ] 没有 launchd 注册。
- [ ] 没有整个工程复制或额外候选 worktree。

## 十、本工程切换待办

- [ ] 在临时工程完成全部测试前，不修改本工程现有方案。
- [ ] 使用本地 `npm pack` 产物在本工程进行影子接入。
- [ ] 影子阶段保留旧 Agent、Command、Skill 和 Shell 脚本。
- [ ] 对比插件安装结果与 `方案备份/snapshot/repository/`。
- [ ] 展示完整 Git diff、测试结果和回滚路径供人工复核。
- [ ] 经人工确认后，在单独提交中切换 `opencode.json`。
- [ ] 插件连续验证成功前，不删除旧方案资源。
- [ ] 如发生异常，按 `方案备份/恢复说明.md` 恢复。

## 十一、发布准备待办

- [x] 核对 npm Registry 状态：正式包名已存在，`0.1.0` 为早期空壳版本。
- [x] 确认后续版本继续使用 public 发布方式。
- [ ] 确认 npm scope 和发布账号权限。
- [ ] 确认许可证和第三方声明。
- [x] 确认下一开发版本为 `0.2.0`；不得覆盖已发布的 `0.1.0`。
- [ ] 生成并人工检查 npm tarball。
- [ ] 生成 CHANGELOG 和 release notes。
- [ ] 确认包内没有本机绝对路径、任务日志、密钥或历史运行证据。
- [ ] 在干净环境从 tarball 完成一次全新安装。
- [ ] 保存两个 OpenCode 版本的最终测试证据。
- [ ] 展示发布命令、目标 registry、包名、版本及可见性。

## 十二、发布授权关口

正式发布是独立的外部变更，必须在下列信息全部明确后再次取得人工确认：

- 正式包名和 scope；
- 版本号；
- public 或 private；
- npm registry；
- Git 仓库地址；
- 许可证；
- npm 登录状态或安全发布凭证；
- 2FA 处理方式；
- 最终 tarball SHA-256；
- 1.14.22 和 1.15.13 的测试结论。

未经该次明确确认，不得执行 `npm publish`、创建远端仓库、推送 tag 或生成
公开 release。不得把 npm token、2FA 信息或其他凭证写入仓库和日志。

已有 `0.1.0` 的发布事实只作为历史状态记录；任何 `0.2.0` 或后续版本的
发布仍必须重新通过本节授权关口。

## 十三、发布后待办

- [ ] 从 npm registry 安装刚发布的固定版本，而不是本地源码。
- [ ] 在本机 OpenCode 1.15.13 完成安装与完整回归。
- [ ] 在目标电脑 OpenCode 1.14.22 完成安装与完整回归。
- [ ] 验证目标 Android 工程无需手动复制任何 Shell 脚本。
- [ ] 验证 `doctor` 能报告版本、资源、权限和依赖状态。
- [ ] 验证升级和卸载不会破坏用户修改。
- [ ] 根据真实安装结果决定是否切换当前工程。
- [ ] 如发现严重问题，停止推广并发布修复版本或执行回滚，不覆盖原版本内容。

## 十四、明确不在本任务中的事项

- 不修改 Android 产品功能代码。
- 不把每个 Android 工程整体复制到插件工作区。
- 不把历史任务合同、计划或 `.git/automation-runtime` 安装到新工程。
- 不自动注册 Scheduler 或 launchd。
- 不自动 push、merge、rebase 或解决原分支漂移。
- 不在第一版重写已验证的 Git、状态机和质量门 Shell 实现。
- 不在用户确认前删除本工程旧方案资源。
- 不在用户确认前执行真实 npm 发布。

## 十五、完成定义

只有同时满足以下条件，任务才能标记为完成：

1. 插件源码、CLI、模板、文档和许可证齐全。
2. `init` 能在一个新的 Android 工程中一键安装全部必要资源。
3. 用户不需要手工复制 `scripts/automation/` 或其他方案文件。
4. OpenCode 1.14.22 与 1.15.13 的兼容矩阵全部通过。
5. 当前 38 项自动化测试保持全绿，并新增插件安装生命周期测试。
6. 安装、升级、卸载和失败回滚均经过实际验证。
7. npm tarball 经人工检查并记录摘要。
8. 用户明确批准正式发布。
9. npm 发布成功且可按固定版本重新安装。
10. 本机和目标电脑分别从已发布包完成最终验收。

## 十六、已确认的启动决策

以下启动决策已经确认：

1. 正式包名为 `@frankzhang2026/opencode-android-orchestrator`。
2. 使用独立 Git 仓库 `frankzhangtx/npm-orchestrator-plugin`。
3. 使用 public 发布方式。
4. 许可证选择 MIT；实际 `LICENSE` 和第三方声明仍属于发布准备工作。
5. Superpowers 继续作为固定伴随依赖，不内置到第一版。
6. 第一版插件工具只提供只读诊断，事务入口继续使用现有 Shell 白名单。

上述决策已使任务进入 `IN_PROGRESS`；当前下一阶段是补齐动态模板和安全安装
生命周期，而不是执行当前工程切换或 npm 发布。
