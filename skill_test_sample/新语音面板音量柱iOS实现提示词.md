# 新语音面板音量柱 iOS 实现提示词

你是一名资深 iOS 工程师。请根据下面说明，在 iOS 上实现一个和 Android 新语音面板一致的音量柱可视化组件。目标不是简单的柱状图，而是一个带随机波形轮廓、音量响应、独立柱高缓动和透明度变化的录音音量动画。

## 实现目标

在语音录制面板的蓝色圆角条中绘制一组白色竖向音量柱。音量柱需要满足：

1. 低音量时保持短柱，不要完全消失。
2. 音量变大时，中间区域柱子更活跃，两侧柱子弱一些。
3. 柱子高度不能瞬间跳到目标值，需要逐帧平滑过渡。
4. 整体波动速度为当前 Android 版本的节奏：波形周期 1061ms，随机轮廓刷新间隔 214ms。
5. 波形不是每根柱子机械正弦，而是由随机热点轮廓决定，听起来有声音时出现自然起伏。
6. 可直接用于 Objective-C 或 Swift；如果没有特别要求，优先用 `UIView + CADisplayLink + CoreGraphics` 实现。

## 坐标与布局

假设蓝色录音条已经有一个 `panelRect`：

- 录音条高度：`44pt`
- 录音条左右边距：`15pt`
- 录音条圆角：`22pt`
- 音量柱绘制在录音条垂直中心线上
- 音量柱整体宽度占录音条宽度的 `0.64`
- 音量柱最大总宽度：`280pt`
- 单个柱宽：`2pt`
- 柱间距：`3pt`
- 最小柱数：`24`
- 单柱最小高度：`3pt`
- 单柱最大高度：`22pt`
- 单柱圆角：`2pt`

柱数计算：

```text
availableWidth = panelRect.width * 0.64
visualizerWidth = min(280, availableWidth)
slotWidth = barWidth + barGap
barCount = max(24, floor(visualizerWidth / slotWidth))
totalWidth = barCount * barWidth + (barCount - 1) * barGap
startX = panelRect.centerX - totalWidth / 2
baselineY = panelRect.centerY
```

## 输入音量

组件接收一个 `0...100` 的音量值：

```text
targetVolume = clamp(inputVolume, 0, 100)
```

如果使用 `AVAudioRecorder`，可以通过 `updateMeters` 后读取 `peakPowerForChannel`，再转换成 `0...100`。建议转换方式：

```text
linear = pow(10, peakPower / 20)
inputVolume = round(sqrt(linear) * 100)
```

不要直接用输入音量绘制。需要在每帧做平滑：

```text
displayedVolume += (targetVolume - displayedVolume) * 0.82
volumeRatio = clamp(displayedVolume / 100, 0, 1)
```

## 动画循环

使用 `CADisplayLink` 每帧刷新。

状态：

```text
wavePhase: 0...1
displayedVolume: Float
targetVolume: Float
currentBarProfile[barCount]
targetBarProfile[barCount]
currentBarHeights[barCount]
waveProfileHotspotCount: Int
lastWaveProfileUpdatedAt: Time
```

每帧：

```text
elapsed = currentTime - animationStartTime
wavePhase = (elapsed % 1.061) / 1.061
displayedVolume += (targetVolume - displayedVolume) * 0.82
setNeedsDisplay()
```

`wavePhase` 只提供整体呼吸节奏。真正的各柱差异来自随机波形轮廓。

## 随机波形轮廓

每次绘制前调用 `updateRandomWaveProfile(barCount, volumeRatio)`。

常量：

```text
RANDOM_WAVE_PROFILE_INTERVAL = 0.214 seconds
RANDOM_WAVE_PROFILE_EASING = 0.72
RANDOM_WAVE_SILENCE_THRESHOLD = 0.04
RANDOM_WAVE_COUNT_VOLUME_POWER = 0.72
RANDOM_WAVE_CENTER_BIAS_PROBABILITY = 0.78
RANDOM_WAVE_CENTER_STANDARD_DEVIATION_RATIO = 0.22
RANDOM_WAVE_MAX_HOTSPOTS = 9
RANDOM_WAVE_MIN_BAR_SPAN = 1.1
RANDOM_WAVE_MAX_BAR_SPAN = 2.8
RANDOM_WAVE_MIN_STRENGTH = 0.48
RANDOM_WAVE_MAX_STRENGTH = 1.0
RANDOM_WAVE_MIN_SHAPE_POWER = 0.72
RANDOM_WAVE_MAX_SHAPE_POWER = 1.65
```

如果 `barCount` 变化，需要重建三个数组：

```text
currentBarProfile = zeros(barCount)
targetBarProfile = zeros(barCount)
currentBarHeights = minBarHeight repeated barCount
waveProfileHotspotCount = -1
lastWaveProfileUpdatedAt = 0
```

热点数量：

```text
if volumeRatio < 0.04:
    hotspotCount = 0
else:
    maxHotspotCount = min(9, max(1, barCount / 3))
    responsiveVolumeRatio = pow(volumeRatio, 0.72)
    hotspotCount = clamp(1 + round((maxHotspotCount - 1) * responsiveVolumeRatio), 1, maxHotspotCount)
```

当以下任一条件成立时，重新生成 `targetBarProfile`：

```text
barCount changed
hotspotCount changed
currentTime - lastWaveProfileUpdatedAt >= 0.214 seconds
```

生成目标轮廓：

```text
targetBarProfile 全部清零

repeat hotspotCount times:
    center = pickCenterBiasedBarIndex(barCount)
    span = random(1.1, 2.8)
    strength = random(0.48, 1.0)
    shapePower = random(0.72, 1.65)
    startIndex = max(0, floor(center - span))
    endIndex = min(barCount - 1, ceil(center + span))

    for index in startIndex...endIndex:
        distanceRatio = abs(index - center) / span
        falloff = 1 - clamp(distanceRatio, 0, 1)
        profile = strength * pow(falloff, shapePower)
        targetBarProfile[index] = max(targetBarProfile[index], profile)
```

中心偏置选择：

```text
maxIndex = barCount - 1
centerIndex = maxIndex / 2

if random(0, 1) < 0.78:
    offset = gaussianRandom() * barCount * 0.22
    return clamp(centerIndex + offset, 0, maxIndex)
else:
    return random(0, maxIndex)
```

`gaussianRandom()` 可用 Box-Muller 方法，或用平台自带近似正态随机数。

每帧把当前轮廓缓动到目标轮廓：

```text
for index in 0..<barCount:
    currentBarProfile[index] += (targetBarProfile[index] - currentBarProfile[index]) * 0.72
```

## 单根柱高度公式

常量：

```text
FULL_CIRCLE = PI * 2
VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER = 2.15
VISUALIZER_BAR_HEIGHT_EASING = 0.22
```

每根柱子的包络：

```text
centerIndex = (barCount - 1) / 2
distanceFromCenter = centerIndex == 0 ? 0 : abs(index - centerIndex) / centerIndex
envelope = clamp(1 - distanceFromCenter * 0.72, 0.24, 1)
```

整体脉冲：

```text
pulse = (sin(wavePhase * FULL_CIRCLE) + 1) / 2
```

音量放大：

```text
amplifiedVolumeRatio = clamp(volumeRatio * 2.15, 0, 1)
```

目标高度：

```text
targetHeight =
    minBarHeight
    + (maxBarHeight - minBarHeight)
    * (
        0.12
        + volumeRatio * 0.08 * envelope
        + amplifiedVolumeRatio
          * (0.46 + 0.54 * pulse)
          * currentBarProfile[index]
          * envelope
      )
```

实际绘制高度必须再做单柱缓动，避免瞬间跳动：

```text
currentBarHeights[index] += (targetHeight - currentBarHeights[index]) * 0.22
activeHeight = currentBarHeights[index]
```

绘制位置：

```text
x = startX + index * slotWidth
top = baselineY - activeHeight / 2
bottom = baselineY + activeHeight / 2
rect = (x, top, barWidth, activeHeight)
```

透明度：

```text
alphaInt = clamp(int(150 + 105 * envelope * (0.35 + volumeRatio * 0.65)), 140, 255)
alpha = alphaInt / 255
color = white with alpha
```

绘制为圆角矩形：

```text
drawRoundedRect(rect, cornerRadius: 2, color: whiteAlpha)
```

## 重置规则

录音面板开始或结束时调用 reset：

```text
displayedVolume = 0
targetVolume = 0
wavePhase = 0
currentBarProfile 全部置 0
targetBarProfile 全部置 0
currentBarHeights 全部置 minBarHeight
waveProfileHotspotCount = -1
lastWaveProfileUpdatedAt = 0
```

不要把 `currentBarHeights` 清成 0，否则首帧会从底部突然长出来；应该清成 `minBarHeight`。

## iOS 落地建议

请实现一个独立视图，例如：

```text
VoiceRecordVisualizerView : UIView
```

公开接口：

```text
updateVolume(value: Int)
startAnimating()
stopAnimating()
reset()
```

内部建议：

```text
CADisplayLink 驱动 wavePhase 和 displayedVolume
drawRect / draw(_:) 内计算并绘制所有柱子
用 Swift Array<Float> 或 Objective-C C 数组保存 profile 和 heights
用 CoreGraphics / UIBezierPath 绘制圆角柱
用 CACurrentMediaTime() 作为随机轮廓刷新时间
```

如果使用 Swift：

```text
var currentBarProfile: [CGFloat]
var targetBarProfile: [CGFloat]
var currentBarHeights: [CGFloat]
```

如果使用 Objective-C：

```text
float *currentBarProfile;
float *targetBarProfile;
float *currentBarHeights;
NSUInteger barCount;
```

Objective-C 里数组重建时记得 `free` 旧内存，`calloc` 新内存。

## 验收标准

1. 无声音时，柱子保持短小且稳定。
2. 说话时，柱子从当前高度平滑变化，不允许整组瞬间跳到目标高度。
3. 中间柱子更明显，两侧柱子更弱。
4. 波形不应呈现固定机械正弦队列，应有随机热点带来的自然跳动。
5. 音量变大时热点数量增加，视觉更丰富。
6. 音量下降时柱子自然回落，而不是突然归零。
7. 录音条宽度变化时，柱子数量自动变化，并正确重建内部数组。
8. 每次打开面板时从最小柱高开始，不出现上一轮录音残留。

## 需要避免的问题

1. 不要每帧直接使用 `targetHeight` 绘制，要经过 `currentBarHeights` 缓动。
2. 不要只用 `sin(index + phase)`，那会显得机械；必须加入随机 profile。
3. 不要把低音量时的高度设为 0，最小高度应为 `3pt`。
4. 不要每帧重新随机所有柱子，只能按 `214ms` 间隔刷新目标轮廓。
5. 不要在 `drawRect` 里频繁创建不必要的大对象；如果性能敏感，可缓存颜色和基础参数。
6. 不要忘记在 `stopAnimating` 时 invalidate `CADisplayLink`，否则会泄漏或持续耗电。

请根据以上规则实现 iOS 版本的音量柱可视化。实现可以使用 Swift 或 Objective-C，但必须完整保留上述动画算法和常量。
