# Review Conclusion

现有方案虽然把“文本 -> 目标页”这条链路打通了，但产品体验方向仍有明显偏差。

问题不在解析精度，而在“执行方式”。

当前方案更接近：

- 用户输入一句话
- 系统识别意图
- 直接跳到最终目标页

但你现在明确要的体验是：

- 用户输入一句话
- 系统理解意图
- 系统按当前 App 已有的页面结构，模拟用户本来会做的操作
- 用户能感知到中间页面流转，而不是被瞬移到最终页

例如输入“打开 Work 看板”时，预期不应是从 `FirstFragment` 直接打开 `HouseDashboardActivity`，而应是：

1. 先从 `FirstFragment` 进入 `SecondFragment`
2. 再由 `SecondFragment` 进入 `HouseDashboardActivity`
3. 最终打开 `WORK` tab

这意味着这版方案必须从“目标页直达模型”升级为“语义识别 + 用户操作回放模型”。

结论：

- `IntentParser`、`SlotNormalizer`、`RoutePlanner` 这些边界仍然成立
- 但 `RoutePlanner` 的输出不应再只是一个终点 `RouteTarget`
- 它必须输出一份“如何像用户一样到达目标”的 `JourneyPlan`
- 执行层也不应再只是 `navigate(target)`，而应改成顺序执行一组可观测步骤

# Product Direction Reset

## 目标不再是“最快到达”，而是“最像真实用户操作”

这次智能路由的首要目标应定义为：

- 保持语义理解能力
- 保持流程编排能力
- 但执行上优先遵循当前应用真实存在的页面路径和按钮语义

也就是说，智能路由不再默认做“深链直达”，而是默认做“操作回放”。

## 默认体验原则

### 1. 优先回放已有页面路径

如果用户从 `FirstFragment` 发起请求，而真实用户通常需要先到 `SecondFragment` 再去其他页面，那么智能路由也应按这个路径执行。

### 2. 直接跳终态只能作为优化策略，不应作为默认策略

只有在以下场景下，才允许绕过中间页直接打开目标：

- 当前页面本身就是目标的直接父页面
- 产品明确要求“极速直达”
- 或某页面已经有明确、稳定、对用户可解释的 deep link 契约

首期默认不走这条路。

### 3. Parser 仍然不能决定导航细节

大模型或规则解析器只负责回答：

- 用户想做什么
- 目标对象是什么
- 有没有歧义

它不负责回答：

- 先点哪个按钮
- 经过哪个 Fragment
- 是否直接跳 Activity

这些仍然由本地规划器决定。

### 4. “目标页”和“到达路径”必须同时建模

旧模型里只有终点，缺少“怎么去”。

新模型必须同时描述：

- 最终目标是什么
- 从当前页面如何一步步到达

# Current App Reality

## 当前工程的真实页面路径

当前工程里，用户可见的主要路径是：

- `FirstFragment`
  - 点击 `button_first`
  - 进入 `SecondFragment`
- `SecondFragment`
  - 点击 `button_personal_info`
  - 进入 `PersonalInfoFragment`
  - 点击 `button_personal_info_list`
  - 进入 `PersonalInfoListFragment`
  - 点击 `button_house_dashboard`
  - 打开 `HouseDashboardActivity`
  - 点击 `button_insurance_mall`
  - 打开 `InsuranceMallActivity`
- `PersonalInfoListFragment`
  - 点击列表项
  - 打开 `PersonalInfoDetailActivity`

这个结构非常重要，因为它定义了“智能路由应该模仿什么”。

## 当前方案的问题

如果仍按“识别到终点后直接打开”的方式实现，会出现以下体验问题：

1. 用户看不到中间路径，容易失去方向感
2. 智能入口和手动入口体验割裂
3. 用户无法建立“这句话对应 App 里哪条操作链”的心智模型
4. 后续如果要做操作解释、回放提示、失败重试，会缺少中间步骤语义
5. 某些目标页本来依赖上一级页面上下文，直跳会让行为变得突兀

# New Design Principles

## 1. 从 RouteTarget 升级为 JourneyPlan

旧模型：

- `ParseResult -> RouteTarget -> AppNavigator.navigate(target)`

新模型：

- `ParseResult -> JourneyPlan -> JourneyExecutor.execute(plan)`

其中：

- `RouteTarget`
  - 表示最终想命中的业务目标
- `JourneyPlan`
  - 表示从当前页面出发，到达该目标所需的完整用户可感知步骤

## 2. JourneyPlan 是“用户操作脚本”，不是“技术导航脚本”

计划中的每一步应该尽量贴近真实用户语义，而不是只写底层技术动作。

推荐 step 语义：

- `GoToSecondPage`
- `OpenPersonalInfoList`
- `FocusListItem`
- `OpenListItemDetail`
- `OpenHouseDashboard`
- `SwitchDashboardTab(WORK)`
- `EnterPersonalInfoWorkflow`
- `OpenReviewStep`
- `OpenResultStep`

执行层再把这些语义步骤映射成：

- `NavController.navigate(...)`
- `startActivity(...)`
- `ListView.smoothScrollToPosition(...)`
- `performItemClick(...)`
- 设置启动参数

## 3. 中间页面要对用户可见

如果路径天然包含 `FirstFragment -> SecondFragment -> HouseDashboardActivity`，就不应把这两步压缩成一次不可见的直跳。

允许的行为是：

- 先进入 `SecondFragment`
- 再继续执行下一步

而不是：

- 直接从 `FirstFragment` 打开 `HouseDashboardActivity`

## 4. 执行器负责“像用户操作”，而不是“绕过页面”

智能路由执行层不应成为 deep link 跳板。

它更接近一个本地的“操作编排器”：

- 知道当前在哪个页面
- 知道下一步该触发哪个页面动作
- 在合适时机执行下一步
- 如果中间页面没就绪，则等待页面稳定后再继续

## 5. 失败回退也要符合用户心智

例如“查看张雨桐的信息”：

- 如果唯一命中记录，可以进入列表页后再自动打开详情
- 如果不唯一，则应停在列表页并高亮候选
- 不应在歧义时直接打开一个猜测详情页

这同样是“像用户操作”的原则，而不是“直接给结果”。

# Optimized Architecture

## 1. 解析层保持不变：只产出业务语义

这一层仍建议保留：

- `IntentParser`
- `CompositeIntentParser`
- `RuleBasedIntentParser`
- `LlmIntentParser`
- `IntentParsingGateway`
- `ParseResultValidator`
- `SlotNormalizer`

解析输出仍然只包含：

- `UserGoal`
- `slots`
- `confidence`
- `ambiguityReason`
- `parserMetadata`

不让 parser 决定：

- 中间页面路径
- UI 按钮点击顺序
- 是否直接跳 Activity

## 2. 规划层改为两段式

建议把规划层拆成两层，而不是一步直出 `RouteTarget`。

### 第一段：目标规划

- `TargetPlanner`

输入：

- `ParseResult`
- 当前会话状态

输出：

- 最终 `RouteTarget`
- 辅助数据
  - `RecordRef`
  - `ListFocusRequest`
  - `HouseDashboardLaunchSpec`
  - 工作流上下文

### 第二段：路径规划

- `JourneyPlanner`

输入：

- 当前所在页面
- `RouteTarget`
- 目标辅助数据

输出：

- `JourneyPlan`

这层的职责是把“要去哪”转成“怎么去”。

## 3. 新的核心对象建议

### `RouteTarget`

保留，用于表达最终业务目标：

- `WorkflowTarget`
- `ListTarget`
- `DetailTarget`
- `HouseDashboardTarget`

### `JourneyPlan`

新增，用于表达完整执行路径：

- `journeyId`
- `entryScreen`
- `target`
- `steps`
- `fallbackPolicy`
- `completionCondition`

### `JourneyStep`

建议至少包含以下语义步骤：

- `NavigateToFragment(destinationId, displayName)`
- `OpenActivity(activityKey, launchArgs)`
- `TriggerSemanticAction(actionKey)`
- `FocusList(focusRequest)`
- `OpenFocusedRecordDetail`
- `ApplyDashboardTab(tab)`
- `ShowFallbackMessage(message)`
- `StopAtCurrentScreen(reason)`

这里的重点是：

- step 允许是“页面动作语义”
- 不要求每一步都等于一次真实点击
- 但整体效果必须让用户感知到路径被逐步回放

## 4. 执行层从 AppNavigator 升级为 JourneyExecutor

旧执行层：

- 收到一个 `RouteTarget`
- 直接导航

新执行层：

- 收到一个 `JourneyPlan`
- 顺序执行 steps
- 在页面切换后等待宿主稳定
- 必要时在下一个页面继续执行剩余步骤

建议新增：

- `JourneyExecutor`
- 或 `NavigationCoordinator`

职责：

- 执行步骤
- 跟踪当前步骤索引
- 避免重复执行
- 在页面未 ready 时挂起等待
- 在中间步骤失败时触发回退策略

## 5. 页面能力模型要增加“入口动作”描述

原来的 `DestinationContractRegistry` 只描述终点能力还不够。

现在建议增加两类声明：

### 页面能力

- 页面类型
- 是否支持列表定位
- 是否支持详情展示
- 是否支持表单回填
- 是否支持提交

### 页面入口动作

即“从哪个父页面，如何进入该页”。

例如：

- `SecondFragment`
  - 暴露动作：
    - `OPEN_PERSONAL_INFO`
    - `OPEN_PERSONAL_INFO_LIST`
    - `OPEN_HOUSE_DASHBOARD`
    - `OPEN_INSURANCE_MALL`
- `PersonalInfoListFragment`
  - 暴露动作：
    - `FOCUS_RECORD`
    - `OPEN_FOCUSED_DETAIL`

这样路径规划器才知道：

- 要去 `HouseDashboardActivity`
- 先必须去 `SecondFragment`
- 到了 `SecondFragment` 后可执行 `OPEN_HOUSE_DASHBOARD`

# Target Journey Examples

## 1. 输入“打开 Work 看板”

期望 JourneyPlan：

1. `NavigateToFragment(SecondFragment)`
2. `OpenActivity(HouseDashboardActivity, entrySource=intelligent-routing)`
3. `ApplyDashboardTab(WORK)`

用户感知效果：

1. 先看到第二页
2. 再进入看板页
3. 最终停在 `WORK`

## 2. 输入“查看第 12 条记录”

期望 JourneyPlan：

1. `NavigateToFragment(SecondFragment)`
2. `NavigateToFragment(PersonalInfoListFragment)`
3. `FocusList(position=12)`
4. `StopAtCurrentScreen(reason=record_focused)`

用户感知效果：

1. 先看到第二页
2. 再看到列表页
3. 列表自动滚到第 12 条并高亮

## 3. 输入“查看张雨桐的信息”

若唯一命中：

1. `NavigateToFragment(SecondFragment)`
2. `NavigateToFragment(PersonalInfoListFragment)`
3. `FocusList(queryName=张雨桐)`
4. `OpenFocusedRecordDetail`

若存在歧义：

1. `NavigateToFragment(SecondFragment)`
2. `NavigateToFragment(PersonalInfoListFragment)`
3. `FocusList(queryName=张雨桐)`
4. `ShowFallbackMessage(存在多个候选，请手动确认)`
5. `StopAtCurrentScreen(reason=ambiguous_record)`

## 4. 输入“帮我补全个人资料”

如果产品期望也保持“像用户操作”的一致体验，则应定义为：

1. `NavigateToFragment(SecondFragment)`
2. `NavigateToFragment(IntentEntryFragment 或 PersonalInfoFormStepFragment)`
3. 进入工作流步骤页
4. 自动回填字段

是否必须经过 `SecondFragment`，取决于产品是否把它视为所有智能动作的统一一级入口。

建议首期统一：

- 只要输入来自 `FirstFragment`
- 默认都先经过 `SecondFragment`

这样路径最稳定，用户心智也最统一。

# Session State And Effects

## 1. 状态层要新增 Journey 维度

`RoutingSessionViewModel` 除了已有的解析状态，还建议新增：

- `activeJourneyId`
- `activeRouteTarget`
- `currentJourneyStepIndex`
- `journeyStatus`
  - `IDLE`
  - `PLANNED`
  - `EXECUTING`
  - `PAUSED`
  - `COMPLETED`
  - `FAILED`
- `lastJourneyError`

## 2. 一次性副作用不再只是单点导航

旧模型下，`UiEffect` 主要是：

- `NavigateTo(target)`

新模型下，建议扩成：

- `ExecuteJourney(plan)`
- `ShowRoutingFallback(message)`
- `ShowParsingUnavailable(message)`
- `ShowValidationError(message)`
- `ShowJourneyStepHint(message)`

必要时也可继续保留原子 effect：

- `NavigateToFragment(...)`
- `OpenActivity(...)`
- `FocusList(...)`
- `OpenFocusedDetail`

但语义上应认为它们是 `JourneyPlan` 的执行结果，而不是独立随机触发的副作用。

## 3. 中间步骤索引可以短暂持有，但仍不能把动作本身长期常驻状态

仍要坚持：

- 可恢复状态进入 `SessionState`
- 一次性动作不长期保存在可观察状态中

否则旋转屏幕后容易重复：

- 再次进入第二页
- 再次打开看板
- 再次自动点开详情

# Parser And LLM Requirements

## 1. 解析输出仍只产出语义，不产出 Journey

无论规则还是 LLM，都只能输出：

- `OpenHouseDashboard + dashboardTab=WORK`
- `BrowsePersonalInfoList + position=12`
- `OpenPersonalInfoDetail + personName=张雨桐`

不能输出：

- “先去 SecondFragment”
- “点击 button_house_dashboard”
- “执行 action_SecondFragment_to_PersonalInfoListFragment”

这些必须由本地路径规划器决定。

## 2. LLM 的价值在于理解，不在于控制 UI

后续接真实模型时，模型适合做：

- 模糊表达识别
- 同义词归一化
- 隐含意图判断

模型不适合直接做：

- UI 自动化决策
- 导航拓扑推断
- Activity / Fragment 内部结构判断

# List And Detail Design Under Replay Mode

## 1. 列表页不再只是终点页，而是详情页的上游步骤页

在新的体验里，`PersonalInfoListFragment` 不只是“可被命中”。

它还经常扮演：

- 详情页前的一步
- 用户确认候选的承接页
- 自动滚动反馈页

因此列表页应该优先支持：

- 可解释的定位状态提示
- 清晰的候选高亮
- 自动打开详情前的可控策略

## 2. 详情页默认通过列表页进入

如果请求是“查看某人的信息”，默认推荐路径是：

- 先去列表页
- 再自动打开唯一命中详情

而不是默认：

- 直接 `startActivity(PersonalInfoDetailActivity)`

原因：

- 更符合用户心智
- 列表页能承接歧义情况
- 用户更容易理解系统是如何定位到该记录的

只有在产品明确要求“信息页可以无列表直达”时，才考虑把 direct detail 打开作为优化路径。

# First-Phase Scope Reset

## 首期要交付的不只是“能到”，而是“按路径到”

首期验收标准应改为：

- 输入“打开 Work 看板”
  - 会先进入 `SecondFragment`
  - 再打开 `HouseDashboardActivity`
  - 最终停在 `WORK`
- 输入“查看第 12 条记录”
  - 会先进入 `SecondFragment`
  - 再进入 `PersonalInfoListFragment`
  - 再自动滚动并高亮
- 输入“查看张雨桐的信息”
  - 会先进入 `SecondFragment`
  - 再进入 `PersonalInfoListFragment`
  - 唯一命中时再打开详情
  - 歧义时停留列表页
- 输入表单类请求
  - 路径也应保持可解释
  - 不应直接把用户瞬移到难以理解的中间状态

## 首期仍不做

- `InsuranceMallActivity`
- 真实网络提交
- 真实线上 LLM provider 接入
- 多入口复杂优先级竞争
- 跨任务栈级别的导航优化

# Recommended File Changes

## 需要调整的设计重点

在原先文件建议基础上，新增或改造重点应变为：

- `app/src/main/java/com/example/cctest/navigation/RouteTarget.kt`
  - 保留最终目标语义
- `app/src/main/java/com/example/cctest/navigation/AppNavigator.kt`
  - 不再只是最终跳转器
  - 应演进为可执行分步路径的协调器
- `app/src/main/java/com/example/cctest/navigation/DestinationContractRegistry.kt`
  - 除页面能力外，补入口动作与可达关系
- `app/src/main/java/com/example/cctest/routing/RoutingSessionViewModel.kt`
  - 增加 `JourneyPlan`、步骤索引和执行状态
- `app/src/main/java/com/example/cctest/routing/model/SessionState.kt`
  - 增加 journey 维度
- `app/src/main/java/com/example/cctest/routing/model/UiEffect.kt`
  - 从单点导航扩为 journey 执行
- `app/src/main/java/com/example/cctest/routing/workflow/RoutePlanner.kt`
  - 拆成目标规划和路径规划
- `app/src/main/java/com/example/cctest/SecondFragment.kt`
  - 成为多个目标页的显式中转页
- `app/src/main/java/com/example/cctest/PersonalInfoListFragment.kt`
  - 成为详情前的定位/确认页

## 建议新增的核心模型

- `app/src/main/java/com/example/cctest/navigation/JourneyPlan.kt`
- `app/src/main/java/com/example/cctest/navigation/JourneyStep.kt`
- `app/src/main/java/com/example/cctest/navigation/JourneyExecutor.kt`
- `app/src/main/java/com/example/cctest/navigation/EntryActionRegistry.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/TargetPlanner.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/JourneyPlanner.kt`

# Incremental Plan

## Phase 0：补齐页面可达关系，而不是先补终点直达

目标：

- 把当前 App 里真实存在的页面路径结构化

实施内容：

- 建立 `RouteTarget`
- 建立页面能力 registry
- 建立入口动作 registry
- 明确：
  - `FirstFragment -> SecondFragment`
  - `SecondFragment -> List`
  - `SecondFragment -> HouseDashboard`
  - `List -> Detail`

验收标准：

- 本地代码可以回答“某个目标从当前页该怎么到达”
- 而不只是“最终要去哪里”

## Phase 1：引入 JourneyPlan，替换单点导航思路

目标：

- 从 `RouteTarget` 直跳，升级为“路径规划 + 顺序执行”

实施内容：

- 新增 `JourneyPlan`
- 新增 `JourneyStep`
- 新增 `JourneyExecutor`
- ViewModel 增加 journey 状态
- effect 改为执行 journey

验收标准：

- 对同一个 `RouteTarget`
- 系统可根据当前所在页面生成不同路径

## Phase 2：打通三条首期可见路径

目标：

- 先把最关键的三条用户可感知路径跑通

实施内容：

- “打开 Work 看板”
  - `First -> Second -> Dashboard`
- “查看第 12 条记录”
  - `First -> Second -> List -> Focus`
- “查看张雨桐的信息”
  - `First -> Second -> List -> Detail 或停留列表`

验收标准：

- 用户能看到路径回放
- 不再出现从首页瞬移到终态页

## Phase 3：把工作流类请求也纳入同一执行模型

目标：

- 表单类请求也使用 JourneyPlan，而不是旁路实现

实施内容：

- 工作流入口统一建模为可达目标
- 保持解析、规划、执行三层分离

验收标准：

- 查找型和工作流型请求共享同一套执行骨架

## Phase 4：再接真实 LLM

目标：

- 在 JourneyPlan 骨架稳定后，再把模糊理解切到真实模型

原因：

- 当前主要问题不是理解，而是执行体验
- 如果先接 LLM，再回头改执行模型，会重复返工

验收标准：

- 模糊表达进入同一条 Journey 规划链
- 不因为接入 LLM 而退回“直接跳终态”

# Final Recommendation

最终建议很明确：

- 继续保留“解析层可切换到大模型”的方向
- 但执行模型必须马上从“终点直跳”改成“用户操作回放”

这次方案优化的关键不是再加多少 parser 能力，而是补上下面这条缺失链路：

- `Intent` 被识别出来以后
- 系统必须知道
- “像用户一样，应该先经过哪些页面，再触发哪些动作，最后到达哪里”

如果这层不补，智能路由虽然技术上能用，但产品体验会一直不自然。

所以后续实现建议按这个顺序推进：

1. 先补 JourneyPlan / JourneyExecutor
2. 再让 `SecondFragment`、`PersonalInfoListFragment` 等页面成为显式路径节点
3. 再把现有目标页接到新执行模型
4. 最后再接真实 LLM provider
