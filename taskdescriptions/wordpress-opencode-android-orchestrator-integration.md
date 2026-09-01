# WordPress Android 工程接入 OpenCode Android Orchestrator

## 1. 当前结论

- 目标工程：`/Users/zhanglong/files/program/wordpresslocal/WordPress`
- 插件源码：`/Users/zhanglong/files/npmprogram/opencode_android_orchestrator`
- Android 主应用模块：`:WordPress`
- WordPress 工作分支：`feature-0602-cases-opencode-test`
- 历史问题（`0.4.0` 及以前）：只使用通用 `testDebugUnitTest` 与
  `connectedDebugAndroidTest` 会漏掉 `:WordPress` 的 flavor 任务；旧接入方案又要求从
  另一个工程传入绝对路径 JSON，把项目知识泄漏到了插件使用步骤中。
- `0.5.0` 修复：`init .` 会通过一次仅配置型 Gradle 调用自动发现真实任务并生成
  `gradleVerification`；focused test 合同仍同时记录 `gradleTask` 和 `filter`。
- `0.6.0` 修复：临时任务发现调用固定增加 `--no-configuration-cache`，避免配置缓存命中时
  跳过 init script 的 `projectsEvaluated` 回调并误报空矩阵；项目文件和正常构建缓存行为不变。
- 发布状态：`0.6.0` 已于 2026-09-01 公开发布，npm `latest` 当前指向 `0.6.0`；正式接入仍
  固定写出 `@0.6.0`，不使用浮动 tag。
- 验证状态：插件 Node 测试 `135/135`、Shell 事务测试 `42/42` 均已通过；`0.5.0` 新发现器
  已在真实 WordPress 工程得到预期矩阵，`0.6.0` 另有连续两次发现的配置缓存回归测试。
- 隔离生命周期：`init → doctor → preflight → shadow → upgrade → uninstall` 已在临时 Android
  fixture 中通过；`init` 管理 45 个文件，幂等升级复用 `45/45`，卸载恢复 1 个原文件并移除
  44 个新增文件，无清理警告。

> `0.6.0` 的候选源码提交为 `db6f61b`，发布证据提交为 `f9de12b`。Registry 回下载包与
> 获批候选包逐字节一致，SHA-256 为
> `15ac0a16575c9b337aaa9879686d2a57d28452a1566a2c1d7e105b2e85fbcd18`。正式接入直接使用
> 固定版本 `@0.6.0`；不要使用旧版本、浮动 `latest` 或来源不明的本地 tarball。

本次方案不改变 Agent 权限文件。自动质量证据必须通过受管脚本产生，不能把 Agent 直接执行的
通用 Gradle 命令当成 WordPress 应用 flavor 已覆盖的证明。

## 2. 已确认的工程状态

| 检查项 | 当前结果 |
| --- | --- |
| WordPress Git 状态 | 工作树干净，跟踪同名远程分支 |
| OpenCode | `1.15.13`，位于插件支持范围 `>=1.14.22 <1.16.0` |
| Node.js / npm | `v24.14.0` / `11.11.0` |
| Java | OpenJDK `21.0.11` |
| Android 工程 | Groovy DSL，检测到 8 个真实 Android 模块（1 个应用、7 个库） |
| Gradle Configuration Cache | `gradle.properties` 已启用；`0.6.0` 只对临时发现调用禁用缓存，正常构建不变 |
| 主模块检测 | 已修复 `.apply(false)` 误判；自动识别唯一应用模块 `:WordPress` |
| 已验证应用单元测试任务 | `:WordPress:testWordpressDebugUnitTest` |
| 已验证应用设备测试任务 | `:WordPress:connectedWordpressDebugAndroidTest` |
| `assembleDebug` | 包含 WordPress 应用与库模块的 Debug 构建 |
| `lint` | 包含 WordPress 应用与库模块；应用侧按默认 lint 入口执行 |
| 隔离生命周期 | `init`、`doctor`、严格 preflight、shadow、幂等 upgrade、uninstall 均通过 |
| npm Registry | `0.6.0` 已公开发布，`latest` 指向 `0.6.0` |
| 插件安装状态 | WordPress 工程尚未安装插件；当前应执行 `0.6.0` 首次 `init`，不是 `upgrade` |

## 3. 修复后的执行模型

质量门不再由四个脚本各自硬编码任务，也不要求用户预先编写 JSON。初始化链路为：

```text
init .
  │
  ├── 静态检测 Git / settings / Android 模块
  ├── 临时 0600 Gradle init script + `gradlew help --no-configuration-cache`
  ├── 只接收带固定 marker 的已注册任务路径
  ├── 自动生成 gradleVerification
  └── 进入原有安装事务、Shell 测试与 shadow-run
```

运行期质量门统一读取生成后的：

```text
automation/config.json
        │
        ├── fullUnitTestTasks ──> baseline / 完整单元测试
        ├── focusedTestTasks ───> 合同允许的聚焦测试任务
        ├── assembleTasks ──────> Debug 构建
        ├── lintTasks ──────────> Android lint
        └── deviceTestTasks ────> 合同要求时执行设备测试
```

对应执行链路：

- `claim-task.sh`：运行 `fullUnitTestTasks`，记录 baseline 命令数组。
- `record-red.sh`：从合同中找到 `{ gradleTask, filter }`，记录真实 RED。
- `verify-task.sh`：执行 focused、完整单测、构建、lint，以及可选设备测试。
- `verify-integration.sh`：在集成候选上重复同一矩阵。
- `validate-contract.sh`：拒绝未列入 `focusedTestTasks` 的合同任务。
- `lib.sh`：校验任务字符并使用参数数组调用 `./gradlew`，不使用 `eval`。

## 4. WordPress 自动发现结果

不再需要 WordPress 专用配置文件。下列矩阵最初由 `0.5.0` 在真实工程中自动生成；`0.6.0`
保留相同推导规则，并让启用 Configuration Cache 的工程能够稳定重复执行发现：

```json
{
  "fullUnitTestTasks": [
    "testDebugUnitTest",
    ":WordPress:testWordpressDebugUnitTest",
    ":WordPress:testJetpackDebugUnitTest"
  ],
  "focusedTestTasks": [
    ":WordPress:testWordpressDebugUnitTest",
    ":WordPress:testJetpackDebugUnitTest",
    ":libs:analytics:testDebugUnitTest",
    ":libs:editor:testDebugUnitTest",
    ":libs:fluxc:testDebugUnitTest",
    ":libs:image-editor:testDebugUnitTest",
    ":libs:mocks:testDebugUnitTest",
    ":libs:networking:testDebugUnitTest",
    ":libs:posttypes:testDebugUnitTest"
  ],
  "assembleTasks": [
    "assembleDebug"
  ],
  "lintTasks": [
    "lint"
  ],
  "deviceTestTasks": [
    "connectedDebugAndroidTest",
    ":WordPress:connectedWordpressDebugAndroidTest",
    ":WordPress:connectedJetpackDebugAndroidTest"
  ]
}
```

生成规则：

- 同名无 flavor 任务折叠成 `testDebugUnitTest`、`assembleDebug`、`lint` 和
  `connectedDebugAndroidTest` 选择器，覆盖普通 Android 库模块。
- flavor 单元测试和设备测试保留精确模块路径，避免再次漏掉应用变体。
- `:WordPress` 是唯一应用模块；其名称与 `wordpressDebug` 匹配，因此 WordPress flavor
  排在 focused task 示例首位。
- 全量验证保留所有已发现 Debug flavor；插件不会暗中选择一个 flavor 而漏掉另一个。
- `--gradle-verification-config` 仍可作为非标准企业策略的显式覆盖，但不属于普通安装步骤；
  覆盖文件应放在目标工程内并提交审核，不能引用另一台机器或另一个仓库的绝对路径。

## 5. 新任务合同格式

`targetTests` 不再是过滤器字符串数组。每个测试必须同时声明 Gradle 任务和过滤器：

```json
{
  "targetTests": [
    {
      "gradleTask": ":WordPress:testWordpressDebugUnitTest",
      "filter": "org.wordpress.android.ExampleTest.exampleBehavior"
    }
  ],
  "deviceTestsRequired": false
}
```

合同中的 `gradleTask` 必须存在于
`automation/config.json` 的 `gradleVerification.focusedTestTasks` 中，否则合同校验直接失败。

## 6. `0.6.0` 发布与审计基线

`0.6.0` 已完成发布，WordPress 正式接入不需要再从插件源码构建 tarball。发布时使用的
固定信息如下：

| 项目 | 已验证值 |
| --- | --- |
| 候选源码提交 | `db6f61b23ba070248a062e7c80e0b6e4c6dd6477` |
| 发布证据提交 | `f9de12bd94f269a61855bf74d2ae35a9a04cfa59` |
| 包名与版本 | `@frankzhang2026/opencode-android-orchestrator@0.6.0` |
| 可见性与 Registry | public，`https://registry.npmjs.org/` |
| SHA-256 | `15ac0a16575c9b337aaa9879686d2a57d28452a1566a2c1d7e105b2e85fbcd18` |
| npm SHA-1 | `299d9d8aa4b99fda1f317e9df260de293c030fca` |
| npm integrity | `sha512-6wFD88iQjZX3rC+jI/s4JHrMGWmjYNnY7inlXUTMMuDmfrS1lEh2u08Lt6SphJzjPiS2LrZBCtcfGQKUTbV9qQ==` |
| 包内容 | 134 个文件；191,545 bytes 压缩，926,811 bytes 解包 |

发布前执行并通过了：

```bash
cd /Users/zhanglong/files/npmprogram/opencode_android_orchestrator

npm run typecheck
npm test
./templates/scripts/automation/tests/run-tests.sh
npm run pack:check
```

预期结果：

- TypeScript 编译与类型检查成功。
- Node 测试显示 `135` 个测试全部通过。
- Shell 测试以 `1..42` 结束。
- `npm pack --dry-run` 只包含允许发布的 `dist/`、`docs/`、`templates/` 和包级文件。
- 两次独立打包字节一致；固定版本 Registry 回下载也与候选包逐字节一致。
- 全新 consumer 的插件入口、公开 API、CLI help 和生产依赖审计通过，漏洞数为 `0`。

需要只读复核 Registry 元数据时执行：

```bash
npm view @frankzhang2026/opencode-android-orchestrator@0.6.0 \
  version dist.shasum dist.integrity dist.fileCount dist.unpackedSize
```

以上命令应报告版本 `0.6.0`、npm SHA-1
`299d9d8aa4b99fda1f317e9df260de293c030fca`、134 个文件和 926,811 bytes 解包大小。
WordPress 安装仍使用下一节的固定版本 `npx` 命令，不使用插件源码目录或本地 tarball。

## 7. 正式安装前保护现场

```bash
cd /Users/zhanglong/files/program/wordpresslocal/WordPress
git rev-parse --show-toplevel
git status --short --branch
```

如果 `git status --short` 有输出，先处理或保存现有修改。工作区干净后再创建独立接入分支：

```bash
git switch -c chore/connect-opencode-android-orchestrator
```

`.automation-plugin/` 保存 manifest、原文件备份和事务恢复证据，不应提交。正式安装前在
`.git/info/exclude` 中加入：

```text
.automation-plugin/
```

验证规则：

```bash
git check-ignore -v .automation-plugin/manifest.json
```

## 8. 使用修复版执行 init

`0.6.0` 已正式发布，且 WordPress 当前没有安装 manifest，因此直接执行固定版本的首次初始化：

```bash
cd /Users/zhanglong/files/program/wordpresslocal/WordPress

npx --yes @frankzhang2026/opencode-android-orchestrator@0.6.0 \
  init . \
  --json
```

普通接入不再携带任何项目专用参数：

- `.apply(false)` 已被正确视为根工程插件声明，不再制造根模块歧义。
- 唯一应用模块 `:WordPress` 会自动成为 focused test 的首选模块。
- 根目录缺少 `.automation-worktree-allowlist` 时，`init` 会自动创建只含注释的
  初始文件；已有普通文件会原样保留。
- 临时 Gradle init script 只存在于系统临时目录，权限为 `0600`，查询结束后立即删除。
- 临时发现命令固定为 `gradlew help --no-configuration-cache --init-script ...`；这只覆盖本次
  配置阶段调用，不会修改 WordPress 的 `gradle.properties`，正常构建仍使用项目配置缓存。
- Gradle 查询或矩阵推导失败时，`init` 在安装事务写入前以
  `GRADLE_DISCOVERY_FAILED` 退出，不会静默退回可能漏测的默认矩阵。

如果发现阶段失败，先用与插件一致的缓存边界复现配置错误：

```bash
./gradlew help --no-configuration-cache --console=plain
```

不应通过删除 Configuration Cache 或关闭项目级配置来长期绕过问题；应修复命令报告的
JDK、依赖解析或 Gradle 配置错误。

只有工程确实采用插件无法推导的非标准质量策略时，才使用
`--gradle-verification-config <工程内相对路径>`；这属于受审核的例外，不是开箱即用流程的一部分。
旧的 WordPress 专用 Gradle 验证 JSON 不再使用，也不应复制到 WordPress 工程。

## 9. 安装后核对

首先检查生成配置：

```bash
test -f .automation-worktree-allowlist
jq '.androidProject.primaryModule, .gradleVerification' automation/config.json
jq '.targetTests' automation/tasks/TASK-TEMPLATE.json.example
```

必须看到：

```text
":WordPress"
:WordPress:testWordpressDebugUnitTest
:WordPress:testJetpackDebugUnitTest
:WordPress:connectedWordpressDebugAndroidTest
:WordPress:connectedJetpackDebugAndroidTest
:libs:networking:testDebugUnitTest
```

随后执行诊断与只读验证：

```bash
npx --yes @frankzhang2026/opencode-android-orchestrator@0.6.0 \
  doctor . --json

./scripts/automation/tests/run-tests.sh
./scripts/automation/shadow-run.sh
```

预期：

- `doctor` 返回退出码 `0`，结果包含 `"ok": true`。
- Shell 测试以 `1..42` 结束。
- shadow 输出包含 `"mutationPerformed": false`。
- 原有 `opencode.json` 配置和项目 Skill 路径仍然存在。

`init` 本身会在事务提交前运行 Shell 测试与 shadow-run。严格的
`preflight.sh --source` 会故意拒绝脏工作树，因此应在人工审核并提交安装差异、确认工作树干净后执行：

```bash
./scripts/automation/preflight.sh --source
```

在受限执行器中验证时，OpenCode 还需要写入用户级 SQLite 与缓存。如果执行器只允许写项目目录，
`opencode debug config` 可能以 `PRAGMA wal_checkpoint(PASSIVE)` 失败；应为该只读发现命令提供正常的
用户数据目录写权限，不能把这种环境错误当成项目配置错误。

## 10. 独立核对 Gradle 矩阵

在开始真实任务前，按矩阵逐项人工核对一次：

```bash
./gradlew testDebugUnitTest \
  :WordPress:testWordpressDebugUnitTest \
  :WordPress:testJetpackDebugUnitTest

./gradlew :WordPress:testWordpressDebugUnitTest \
  --tests 'org.wordpress.android.ExampleTest.exampleBehavior'

./gradlew assembleDebug
./gradlew lint
```

只有合同将 `deviceTestsRequired` 设为 `true` 时，自动质量门才执行：

```bash
./gradlew \
  connectedDebugAndroidTest \
  :WordPress:connectedWordpressDebugAndroidTest \
  :WordPress:connectedJetpackDebugAndroidTest
```

设备测试需要已连接设备或可用模拟器。没有设备时必须保持合同为 `false`，不能伪造通过证据。

## 11. upgrade 与 uninstall

升级器默认保留首次 `init` 自动发现并安装的 `gradleVerification`，不再重复引用外部文件：

```bash
npx --yes @frankzhang2026/opencode-android-orchestrator@<新固定版本> \
  upgrade . \
  --json
```

升级后重新执行 `doctor`、Shell 测试和 shadow-run。如果项目新增或删除了 flavor，应在升级前先用
新版本在临时干净 checkout 中执行首次 `init` 来核对自动发现矩阵，并把“是否刷新既有策略”作为
独立审核项，不能悄悄覆盖。`upgrade` 默认保留旧安装的矩阵，不会因为升级到 `0.6.0` 自动刷新；
如果旧矩阵漏掉 flavor，必须使用经审核的工程内覆盖文件，或先安全卸载再重新 `init`。当前 WordPress
没有旧安装，因此不需要走这条迁移路径。

安全卸载：

```bash
npx --yes @frankzhang2026/opencode-android-orchestrator@<已安装版本> \
  uninstall . \
  --json
```

不要手工删除 `.automation-plugin/`、修改 manifest 哈希、运行 `git reset --hard` 或使用
`git clean` 绕过事务错误。

2026-08-28 的隔离验证结果：幂等升级返回 `already-current` 并复用全部 45 个受管文件；卸载返回
`uninstalled`，恢复原始 `opencode.json`、移除其余 44 个受管文件，且受管工作树与安装前基线无差异。
`.automation-plugin/` 的审计/恢复证据会保留；OpenCode 自己生成并忽略的 `.opencode` 依赖缓存也不在
插件 manifest 内，卸载器不会冒险删除这些外部文件。

## 12. 人工接入清单

- [ ] WordPress 工作区干净，并使用独立接入分支。
- [ ] 使用固定的 `@frankzhang2026/opencode-android-orchestrator@0.6.0`，不使用浮动 `latest`。
- [ ] 初始化命令只有 `init . --json`，没有跨仓库绝对路径或 WordPress 专用 JSON。
- [ ] 没有复制或重新创建 WordPress 专用 Gradle 验证 JSON。
- [ ] `androidProject.primaryModule` 自动识别为 `:WordPress`。
- [ ] `automation/config.json` 同时覆盖库单测和 WordPress 应用单测。
- [ ] focused test 首项为 `:WordPress:testWordpressDebugUnitTest`，同时允许其他已发现模块任务。
- [ ] 全量与设备矩阵同时包含 WordPress 和 Jetpack Debug flavor。
- [ ] `init` 已自动创建或保留 `.automation-worktree-allowlist`；无需排除项时保持注释即可。
- [ ] WordPress 的项目级 Configuration Cache 保持启用；插件只在临时发现调用中禁用缓存。
- [ ] `doctor`、135 项 Node 测试、42 项 Shell 测试与 shadow-run 均通过。
- [ ] `.automation-plugin/` 已被忽略且未提交。
- [ ] 安装差异经过人工审核后才提交。
- [ ] 提交后工作树干净，严格的 `preflight.sh --source` 通过。
- [ ] `0.5.0` 及更早版本、源码目录、本地 tarball 或来源不明的包没有用于正式接入。

满足以上条件后，插件才同时达到两个目标：不再漏掉应用 flavor，并且任何 Android 工程都可以从
一条项目无关的 `init .` 命令开始接入。
