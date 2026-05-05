# Intelligent Routing TODO

## 当前状态

- 智能路由主干已落地并完成本轮收尾：
  - 入口页、工作流页、审核页、结果页已接入
  - `CompositeIntentParser -> SlotNormalizer -> ParseResultValidator -> RoutePlanner -> AppNavigator` 已打通
  - 列表页支持 `ListFocusRequest`
  - 详情页支持 `recordId`
  - 家居看板支持最小启动参数
- 本轮新增：
  - 已接入真实远程 LLM 网关 `RemoteIntentParsingGateway`
  - `enableLlmParsing`、规则阈值、超时、LLM 失败回退已收敛到统一配置 `IntentParsingConfig`
  - `AppContainer` 的 `ViewModelProvider.Factory` 已去掉 unchecked cast warning
  - 列表定位状态区已增强，审核页/结果页文案已更新
  - 新增 `androidTest` 覆盖四条核心路径

## 已完成

1. 接真实大模型解析
   - 已完成：默认实现已从假网关切到真实 HTTP 网关
   - 配置入口：
     - `routing.enableLlmParsing`
     - `routing.llmApiKey`
     - `routing.llmBaseUrl`
     - `routing.llmModel`
     - `routing.llmProviderName`
   - 默认行为：
     - 未开启或未配置密钥时，保留规则解析主路径
     - LLM 失败时可按配置回退到规则解析结果

2. 加 feature flag、超时和回退配置
   - 已完成：`IntentParsingConfig` 统一承接以下配置
     - `enableLlmParsing`
     - `fallbackToRuleOnLlmFailure`
     - `minRuleConfidence`
     - `llmRequestTimeoutMs`
     - `llmConnectTimeoutMs`
     - `llmReadTimeoutMs`

3. 补 UI / instrumentation 测试
   - 已完成代码补充：
     - 输入“查看第 12 条记录”后列表自动定位
     - 输入“查看张雨桐的信息”后详情直达
     - 输入“打开 Work 看板”后看板页正确打开
     - 表单流从入口到审核到结果页走通
   - 说明：
     - `androidTest` APK 已成功编译
     - 当前机器未连接设备 / 模拟器，未实际执行 `connectedDebugAndroidTest`

4. 跑 lint 并修问题
   - 已完成：`./gradlew lint` 已通过
   - 已顺手修正：
     - `FloatingActionButton` 无障碍描述
     - 列表页定位反馈展示
     - 审核页 / 结果页文案表达

## 已验证

- 2026-04-13：`./gradlew testDebugUnitTest`
- 2026-04-13：`./gradlew lint`
- 2026-04-13：`./gradlew assembleDebug`
- 2026-04-13：`./gradlew assembleAndroidTest`

## 后续可选优化

1. 接入真实业务提交接口
   - 当前 `SubmissionCoordinator` 仍走 `FakeSubmissionPort`

2. 真机跑通 instrumentation
   - 设备可用后执行：`./gradlew connectedDebugAndroidTest`

3. 收敛依赖注入方式
   - 当前仍使用 `AppContainer` 服务定位
   - 如果后续要接更多 provider / repository，建议迁移到更明确的 DI 方案
