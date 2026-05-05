# Intelligent Routing Plan Rewrite

## Goal

在当前这个最小化 Android Fragment/XML 示例工程上，增加一套可逐步落地的“智能路由系统”。系统接收一段自然语言输入后，完成以下链路：

1. 解析文本，得到意图和结构化实体
2. 选择匹配的工作流
3. 生成当前应进入的页面步骤
4. 将可确定字段自动回填到表单
5. 在缺失信息、低置信度或敏感字段场景下停下等待用户确认
6. 在满足门禁条件后执行提交
7. 展示结果，并支持失败重试与流程恢复

目标不是在 Fragment 内拼接一套“会跳转的表单页”，而是建立一层独立于页面的工作流编排能力。

## Current Project Constraints

当前仓库的真实基础能力非常有限：

- 只有 `MainActivity` + `NavController` 宿主
- 只有两个示例 Fragment
- 使用 XML + ViewBinding
- 已接入 Navigation Component
- 没有网络层
- 没有状态恢复设计
- 没有表单模型、解析器、工作流定义或提交流程

这意味着方案必须优先满足：

- 易于在现有 Fragment 架构中接入
- 第一阶段即可验证闭环
- 不引入过重依赖或过多抽象层
- 从第一阶段开始就避免后续大面积推翻

## Design Principles

### 1. 保留 Fragment/XML + Navigation，不切换技术栈

继续沿用现有 `MainActivity`、`nav_graph.xml` 和 Fragment 模式，不改为 Compose，不引入全新页面框架。当前工程的主要风险不在 UI 技术，而在流程控制复杂度。

### 2. 从第一阶段开始就区分“持久状态”和“一次性副作用”

这是本次重写计划里最重要的约束。

必须明确区分两类数据：

- `SessionState`：可持久化、可恢复、可回放的流程状态
- `UiEffect`：一次性触发的动作，例如导航、Toast、提交请求触发

不能把“请跳转到某页”或“立即提交”直接塞进可观察状态里长期保存，否则在屏幕旋转、Fragment 重建、重复订阅时容易重复导航或重复提交。

推荐原则：

- ViewModel 只长期保存流程状态
- 引擎根据状态变化产生一次性效果
- 页面层只消费效果，不保存效果

### 3. 共享状态里只保存可恢复数据，不保存重量级定义对象

`RoutingSessionViewModel` 不应直接持有完整 `WorkflowDefinition` 实例作为恢复核心数据。共享状态应只保存：

- 原始输入文本
- 解析结果
- `workflowId`
- 当前 `stepId`
- 表单字段值
- 字段来源
- 用户确认标记
- 提交状态
- 错误信息

工作流定义、字段映射、步骤定义应从 `WorkflowRegistry` 按 `workflowId` 动态读取。这样才能支持：

- 屏幕旋转恢复
- 进程重建恢复
- 测试中构造轻量状态
- 后续替换配置来源

### 4. 第一阶段也要采用最终的核心边界，只做“最小实现”

第一阶段可以只支持 1 条工作流，但不能靠硬编码 Fragment 跳转把流程先拼出来，否则第二阶段一定返工。

第一阶段就要保留这些边界：

- `IntentParser`
- `WorkflowRegistry`
- `RoutePlanner`
- `WorkflowEngine`
- `NavigationCoordinator`
- `FieldMappingEngine`
- `SubmissionCoordinator`

区别只在于：

- 第一阶段这些组件只支持最小业务范围
- 配置量小
- 行为简单
- 不接真实接口

## Target Architecture

### 1. UI Layer

页面只承担输入、展示、修改、确认，不做智能决策。

建议页面结构：

- `IntentEntryFragment`
  - 输入自然语言文本
  - 触发解析
  - 展示解析预览或错误提示
- `FormStepFragment` 或少量具体业务表单页
  - 展示当前步骤字段
  - 接收自动回填值
  - 记录用户手改
  - 点击继续后通知引擎推进
- `ReviewSubmitFragment`
  - 展示将要提交的最终字段
  - 高亮低置信度或敏感字段
  - 用户确认提交
- `ResultFragment`
  - 展示成功/失败结果
  - 提供重试、返回首页、重新发起流程

### 2. Session State Layer

新增 `RoutingSessionViewModel` 作为会话状态单一入口。

它负责持有：

- 原始文本
- 解析输出
- 当前工作流 ID
- 当前步骤 ID
- 全局字段字典
- 字段元数据
- 审核确认状态
- 提交状态
- 最近一次错误

建议将字段状态标准化，例如：

- `value`
- `source`
- `isConfirmed`
- `isSensitive`
- `confidence`
- `lastUpdatedBy`

其中 `source` 至少包含：

- `PARSED`
- `USER_EDITED`
- `DEFAULTED`
- `API_RETURNED`

### 3. Parser Layer

新增 `IntentParser` 抽象，第一版实现为 `RuleBasedIntentParser`。

输入：

- 原始文本

输出：

- `intent`
- `entities`
- `confidence`
- `ambiguities`
- `matchedRules`

第一版坚持规则优先：

- 关键词
- 正则
- 简单归一化
- 枚举映射

不引入 LLM 或复杂 NLP。这样更适合当前项目体量，也更利于单元测试。

### 4. Workflow Definition Layer

新增：

- `WorkflowRegistry`
- `WorkflowDefinition`
- `WorkflowStep`
- `FieldMapping`

第一版先用 Kotlin 常量维护，不上 JSON、不上远端配置。

建议定义最少包含：

#### `WorkflowDefinition`

- `id`
- `supportedIntent`
- `steps`
- `submissionPolicy`

#### `WorkflowStep`

- `stepId`
- `destinationId`
- `requiredFields`
- `optionalFields`
- `requiresUserConfirmation`
- `autoAdvanceAllowed`
- `entryCondition`

#### `FieldMapping`

- `stepId`
- `fieldKey`
- `entityKey`
- `transformer`
- `overwritePolicy`

### 5. Planning Layer

新增 `RoutePlanner`，负责将解析结果转换为可执行工作流上下文。

职责：

- 根据意图选择工作流
- 判断是否命中单一工作流
- 判断当前缺失哪些关键字段
- 决定是否进入第一个表单页、审核页或错误分支
- 生成初始步骤定位

第一版不追求复杂策略，只需要支持：

- 命中单一工作流
- 未命中时进入兜底提示
- 缺字段时进入对应表单步骤

### 6. Engine Layer

新增 `WorkflowEngine`，这是整个流程的核心控制器。

它不直接操作视图，而是：

- 读取共享状态
- 根据当前事件推进状态
- 产出下一步 `SessionState`
- 发出一次性 `UiEffect`

建议只处理有限事件，例如：

- `OnTextSubmitted`
- `OnParsingSucceeded`
- `OnParsingFailed`
- `OnFieldEdited`
- `OnContinueClicked`
- `OnReviewConfirmed`
- `OnSubmitRequested`
- `OnSubmitSucceeded`
- `OnSubmitFailed`
- `OnRetryRequested`
- `OnCancelRequested`

建议状态机只保留清晰的阶段：

- `Idle`
- `Parsed`
- `Planned`
- `CollectingInput`
- `Reviewing`
- `ReadyToSubmit`
- `Submitting`
- `Completed`
- `Failed`

### 7. Navigation Layer

新增 `NavigationCoordinator`，用于把工作流步骤转换成 Navigation 行为。

职责只包括：

- 根据 `stepId` 或 `destinationId` 计算目标页面
- 执行一次性导航
- 避免同一步骤重复导航

它不负责业务判断，不负责字段校验。

### 8. Field Mapping Layer

新增 `FieldMappingEngine`，负责把解析结果映射到表单字段。

行为规则建议从第一版就定清楚：

- 默认只填空字段
- `USER_EDITED` 字段不可被覆盖
- 低置信度值只作为建议值
- 敏感字段必须人工确认
- 解析器后续重新执行时，不得静默覆盖用户已确认值

### 9. Submission Layer

新增：

- `SubmissionCoordinator`
- `ApiRepository`
- `ApiService`

但提交能力分阶段引入。

`SubmissionCoordinator` 的核心职责是门禁，不是直接把网络请求塞到页面里。

提交前必须校验：

- 必填字段完整
- 当前工作流步骤已完成
- 无未确认敏感字段
- 无未处理低置信度字段
- 当前流程未处于提交中

## Data and Effect Model

### SessionState

建议文档中明确：以下内容属于可恢复状态。

- 原始文本
- 解析结果
- 当前工作流 ID
- 当前步骤 ID
- 当前阶段
- 字段集合
- 用户确认集合
- 提交状态
- 结果摘要
- 错误摘要

### UiEffect

以下内容不应持久保存在状态中，而应作为一次性效果发送：

- Navigate to page
- Show validation error
- Open review page
- Trigger submit
- Show submit result message

### Why this split matters

这样可以避免：

- 横竖屏切换后重复导航
- Fragment 重建后重复提交
- 观察者重新订阅后重复执行一次性动作

## Incremental Implementation Plan

## Phase 1: 打通最小闭环，但保留最终架构边界

目标：

在不接真实接口的前提下，完成“输入文本 -> 解析 -> 规划 -> 跳页 -> 自动回填 -> 用户修改 -> 审核”的最小闭环。

范围：

- 新增 `IntentEntryFragment`
- 新增一个最小表单页
- 新增 `ReviewSubmitFragment`
- 新增 `ResultFragment` 或用简单结果页占位
- 新增 `RoutingSessionViewModel`
- 新增 `RuleBasedIntentParser`
- 新增最小版 `WorkflowRegistry`
- 新增最小版 `RoutePlanner`
- 新增最小版 `WorkflowEngine`
- 新增最小版 `NavigationCoordinator`
- 新增最小版 `FieldMappingEngine`
- 只支持 1 条业务工作流
- 用本地假提交代替真实网络

第一阶段明确不做：

- 多工作流复杂竞争
- 远端配置
- Retrofit 实网请求
- 会话跨进程持久化

验收标准：

- 文本输入后可以得到确定意图或明确失败提示
- 至少一条工作流可以从入口页推进到审核页
- 解析得到的字段可自动回填
- 用户修改字段后不会被再次覆盖
- 页面跳转由工作流步骤驱动，不再写死在 Fragment 点击事件中
- 旋转屏幕后不会重复导航或重复触发假提交

## Phase 2: 从单工作流扩展到配置驱动的多工作流

目标：

把第一阶段已经存在的最小抽象扩展为真正可配置的工作流体系，而不是重写。

范围：

- 扩展 `WorkflowRegistry`
- 扩展 `WorkflowDefinition`
- 扩展 `WorkflowStep`
- 扩展 `FieldMapping`
- 支持多意图
- 支持缺字段停留
- 支持条件步骤
- 支持跳步与审核前汇总

验收标准：

- 新增一条业务工作流时，主要修改点集中在工作流定义与字段映射
- Fragment 不新增业务判断分支
- `RoutePlanner` 可以根据不同解析结果选择不同工作流

## Phase 3: 接入真实提交能力

目标：

把“假提交”替换为真实网络请求与结果处理。

范围：

- 增加 Retrofit/OkHttp 依赖
- 新增 `ApiService`
- 新增 `ApiRepository`
- 完成 `SubmissionCoordinator` 门禁与提交流程
- 增加提交中、成功、失败、重试状态
- 增加重复提交保护

验收标准：

- 审核通过后可以执行真实提交
- 提交失败后保留用户当前输入
- 用户可以重试，不会丢失上下文
- 同一流程在提交中不会重复触发第二次请求

## Phase 4: 增强安全性、可解释性和恢复能力

目标：

降低误解析、误路由和误提交风险，并提高流程韧性。

范围：

- 完善解析置信度展示
- 增加歧义解释
- 敏感字段强制确认
- 保存可恢复会话状态
- 支持中断后回到最近步骤
- 完善取消流程与返回键策略

验收标准：

- 低置信度场景不会进入静默自动提交
- 用户手改值和已确认值不会被自动覆盖
- 应用中断或重建后可恢复到当前工作流步骤

## File Planning

### Existing files likely to change

- `app/src/main/java/com/example/cctest/MainActivity.kt`
  - 继续作为 `NavController` 宿主
  - 视需要挂接会话范围 ViewModel
- `app/src/main/res/navigation/nav_graph.xml`
  - 增加入口、表单、审核、结果页
- `app/build.gradle.kts`
  - 增加 lifecycle、coroutines、test、network 依赖
- `gradle/libs.versions.toml`
  - 增加依赖版本声明

### New core files

- `app/src/main/java/com/example/cctest/routing/RoutingSessionViewModel.kt`
- `app/src/main/java/com/example/cctest/routing/parser/IntentParser.kt`
- `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/WorkflowRegistry.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/WorkflowDefinition.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/RoutePlanner.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/WorkflowEngine.kt`
- `app/src/main/java/com/example/cctest/routing/navigation/NavigationCoordinator.kt`
- `app/src/main/java/com/example/cctest/routing/mapping/FieldMappingEngine.kt`
- `app/src/main/java/com/example/cctest/routing/submission/SubmissionCoordinator.kt`
- `app/src/main/java/com/example/cctest/data/api/ApiService.kt`
- `app/src/main/java/com/example/cctest/data/repository/ApiRepository.kt`
- `app/src/main/java/com/example/cctest/ui/IntentEntryFragment.kt`
- `app/src/main/java/com/example/cctest/ui/ReviewSubmitFragment.kt`
- `app/src/main/java/com/example/cctest/ui/ResultFragment.kt`

## Verification Plan

### Unit tests

优先覆盖：

- `RuleBasedIntentParser`
- `RoutePlanner`
- `WorkflowEngine`
- `FieldMappingEngine`
- `SubmissionCoordinator`

重点场景：

- 明确意图文本能被正确解析
- 未命中工作流时进入兜底路径
- 缺失字段时停在正确步骤
- `USER_EDITED` 字段不会被重新覆盖
- 低置信度字段不会绕过确认
- 提交门禁不满足时不能触发提交

### UI / Instrumentation tests

重点验证：

- 输入文本后进入正确首个步骤页
- 自动填充值出现在正确字段
- 用户修改后继续流转正常
- 审核确认后才允许提交
- 失败后保留当前状态并允许重试
- 页面重建后不会重复导航或重复提交

### Manual test scenarios

至少覆盖：

- 高置信度且信息完整输入
- 意图歧义输入
- 缺关键字段输入
- 解析值错误后用户手动修正
- 提交失败 / 超时 / 重试
- 返回键回退
- 流程取消后重新开始
- 屏幕旋转或页面重建

## Final Recommendation

这个项目适合采用“规则解析 + 配置化工作流 + 共享会话状态 + 一次性效果驱动导航/提交”的方案，但实现顺序必须调整。

可以保留原方案的大方向，但应把以下内容前置到第一阶段：

- 明确 `SessionState` 与 `UiEffect` 分离
- 只在状态中保存可恢复数据
- 从第一阶段开始就使用最小版工作流抽象，而不是先写死 Fragment 流程

这样做的好处是：

- 第一阶段就能验证真实架构方向
- 第二阶段扩展多工作流时不需要推翻重来
- 后续加入恢复、重试和网络提交时风险更低
