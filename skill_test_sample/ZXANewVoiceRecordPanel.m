#import "ZXANewVoiceRecordPanel.h"

#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>
#import <QuartzCore/QuartzCore.h>

static CGFloat ZXAClamp(CGFloat value, CGFloat minValue, CGFloat maxValue) {
    return MAX(minValue, MIN(maxValue, value));
}

static NSInteger ZXAClampInteger(NSInteger value, NSInteger minValue, NSInteger maxValue) {
    return MAX(minValue, MIN(maxValue, value));
}

static UIColor *ZXAColorFromRGB(NSUInteger rgb) {
    return [UIColor colorWithRed:((rgb >> 16) & 0xFF) / 255.0
                           green:((rgb >> 8) & 0xFF) / 255.0
                            blue:(rgb & 0xFF) / 255.0
                           alpha:1.0];
}

static UIColor *ZXAColorWithAlpha(UIColor *color, CGFloat alpha) {
    CGFloat red = 0.0;
    CGFloat green = 0.0;
    CGFloat blue = 0.0;
    CGFloat currentAlpha = 0.0;
    [color getRed:&red green:&green blue:&blue alpha:&currentAlpha];
    return [UIColor colorWithRed:red green:green blue:blue alpha:alpha];
}

static UIColor *ZXAInterpolateColor(UIColor *startColor, UIColor *endColor, CGFloat progress) {
    CGFloat startRed = 0.0;
    CGFloat startGreen = 0.0;
    CGFloat startBlue = 0.0;
    CGFloat startAlpha = 0.0;
    CGFloat endRed = 0.0;
    CGFloat endGreen = 0.0;
    CGFloat endBlue = 0.0;
    CGFloat endAlpha = 0.0;
    [startColor getRed:&startRed green:&startGreen blue:&startBlue alpha:&startAlpha];
    [endColor getRed:&endRed green:&endGreen blue:&endBlue alpha:&endAlpha];
    return [UIColor colorWithRed:startRed + (endRed - startRed) * progress
                           green:startGreen + (endGreen - startGreen) * progress
                            blue:startBlue + (endBlue - startBlue) * progress
                           alpha:startAlpha + (endAlpha - startAlpha) * progress];
}

static CGFloat ZXARandomUnit(void) {
    return arc4random_uniform(UINT32_MAX) / (CGFloat)UINT32_MAX;
}

static CGFloat ZXARandomGaussian(void) {
    CGFloat first = MAX(ZXARandomUnit(), 0.0001);
    CGFloat second = MAX(ZXARandomUnit(), 0.0001);
    return sqrt(-2.0 * log(first)) * cos((CGFloat)(M_PI * 2.0) * second);
}

static const NSInteger ZXAMinVolume = 0;
static const NSInteger ZXAMaxVolume = 100;
static const NSTimeInterval ZXASampleIntervalSeconds = 0.035;
static const NSTimeInterval ZXAHoldStartDelaySeconds = 0.100;
static const NSTimeInterval ZXAWaveDurationSeconds = 1.061;
static const NSTimeInterval ZXAColorAnimationDurationSeconds = 0.180;
static const NSTimeInterval ZXARandomWaveProfileIntervalSeconds = 0.214;
static const NSInteger ZXAMinBarCount = 24;
static const NSInteger ZXARandomWaveMaxHotspots = 9;
static const CGFloat ZXAFullCircle = (CGFloat)(M_PI * 2.0);
static const CGFloat ZXARandomWaveProfileEasing = 0.72;
static const CGFloat ZXARandomWaveSilenceThreshold = 0.04;
static const CGFloat ZXARandomWaveCountVolumePower = 0.72;
static const CGFloat ZXARandomWaveCenterBiasProbability = 0.78;
static const CGFloat ZXARandomWaveCenterStandardDeviationRatio = 0.22;
static const CGFloat ZXARandomWaveMinBarSpan = 1.1;
static const CGFloat ZXARandomWaveMaxBarSpan = 2.8;
static const CGFloat ZXARandomWaveMinStrength = 0.48;
static const CGFloat ZXARandomWaveMaxStrength = 1.0;
static const CGFloat ZXARandomWaveMinShapePower = 0.72;
static const CGFloat ZXARandomWaveMaxShapePower = 1.65;
static const CGFloat ZXAVisualizerBarHeightEasing = 0.22;
static const CGFloat ZXAVolumeEasing = 0.82;
static const CGFloat ZXAVisualizerVolumeAmplitudeMultiplier = 2.15;
static const CGFloat ZXAFingerEasing = 0.22;
static const CGFloat ZXAVisualizerWidthRatio = 0.64;

@interface ZXAVoiceHoldGestureRecognizer : UILongPressGestureRecognizer
@property (nonatomic, assign) BOOL startImmediately;
@property (nonatomic, copy, nullable) ZXAVoiceRecordHoldCondition condition;
@end

@implementation ZXAVoiceHoldGestureRecognizer
@end

typedef void (^ZXAVolumeChangedHandler)(NSInteger volume);

@interface ZXANewVoiceAmplitudeRecorder : NSObject
@property (nonatomic, strong, nullable) AVAudioRecorder *recorder;
@property (nonatomic, strong, nullable) NSTimer *sampleTimer;
@property (nonatomic, strong, nullable) NSURL *outputURL;
@property (nonatomic, copy, nullable) ZXAVolumeChangedHandler volumeChangedHandler;
- (BOOL)startWithVolumeChangedHandler:(ZXAVolumeChangedHandler)handler;
- (void)stop;
@end

@implementation ZXANewVoiceAmplitudeRecorder

- (BOOL)startWithVolumeChangedHandler:(ZXAVolumeChangedHandler)handler {
    [self stop];
    self.volumeChangedHandler = handler;

    NSError *sessionError = nil;
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory:AVAudioSessionCategoryPlayAndRecord error:&sessionError];
    [session setActive:YES error:&sessionError];

    NSString *fileName = [NSString stringWithFormat:@"pic_voice_record_%@.m4a", NSUUID.UUID.UUIDString];
    NSURL *outputURL = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:fileName]];
    NSDictionary<NSString *, id> *settings = @{
        AVFormatIDKey: @(kAudioFormatMPEG4AAC),
        AVSampleRateKey: @(44100),
        AVNumberOfChannelsKey: @(1),
        AVEncoderBitRateKey: @(64000)
    };

    NSError *error = nil;
    AVAudioRecorder *recorder = [[AVAudioRecorder alloc] initWithURL:outputURL settings:settings error:&error];
    if (recorder == nil || error != nil) {
        return NO;
    }

    recorder.meteringEnabled = YES;
    if (![recorder prepareToRecord] || ![recorder record]) {
        [recorder stop];
        return NO;
    }

    self.outputURL = outputURL;
    self.recorder = recorder;
    self.sampleTimer = [NSTimer scheduledTimerWithTimeInterval:ZXASampleIntervalSeconds
                                                        target:self
                                                      selector:@selector(sampleVolume)
                                                      userInfo:nil
                                                       repeats:YES];
    return YES;
}

- (void)stop {
    [self.sampleTimer invalidate];
    self.sampleTimer = nil;

    if (self.recorder != nil) {
        [self.recorder stop];
        self.recorder = nil;
    }

    if (self.outputURL != nil) {
        [[NSFileManager defaultManager] removeItemAtURL:self.outputURL error:nil];
        self.outputURL = nil;
    }

    self.volumeChangedHandler = nil;
}

- (void)sampleVolume {
    AVAudioRecorder *recorder = self.recorder;
    if (recorder == nil) {
        if (self.volumeChangedHandler != nil) {
            self.volumeChangedHandler(ZXAMinVolume);
        }
        return;
    }

    [recorder updateMeters];
    CGFloat peakPower = [recorder peakPowerForChannel:0];
    CGFloat linear = pow(10.0, peakPower / 20.0);
    NSInteger volume = ZXAClampInteger((NSInteger)round(sqrt(linear) * ZXAMaxVolume), ZXAMinVolume, ZXAMaxVolume);
    if (self.volumeChangedHandler != nil) {
        self.volumeChangedHandler(volume);
    }
}

@end

@interface ZXANewVoiceRecordCanvasView : UIView
@property (nonatomic, weak, nullable) UIView *panelAnchorView;
- (void)reset;
- (void)startAnimating;
- (void)stopAnimating;
- (void)updateVolume:(NSInteger)volume;
- (void)updateFinger:(CGPoint)point active:(BOOL)active;
- (BOOL)setCancelMode:(BOOL)cancel;
- (BOOL)isPointInsideRecordArea:(CGPoint)point;
@end

@interface ZXANewVoiceRecordCanvasView ()
@property (nonatomic, strong) CADisplayLink *displayLink;
@property (nonatomic, assign) CFTimeInterval animationStartTimestamp;
@property (nonatomic, assign) CGFloat displayedVolume;
@property (nonatomic, assign) CGFloat targetVolume;
@property (nonatomic, assign) CGFloat wavePhase;
@property (nonatomic, assign) CGPoint fingerPoint;
@property (nonatomic, assign) CGPoint targetFingerPoint;
@property (nonatomic, assign) CGRect panelRect;
@property (nonatomic, strong) UIColor *panelBackgroundColor;
@property (nonatomic, strong) UIColor *normalPanelColor;
@property (nonatomic, strong) UIColor *normalPanelCenterColor;
@property (nonatomic, strong) UIColor *cancelPanelColor;
@property (nonatomic, strong) UIColor *cancelPanelCenterColor;
@property (nonatomic, strong) UIColor *normalPromptColor;
@property (nonatomic, strong) UIColor *cancelPromptColor;
@property (nonatomic, strong) UIColor *currentPanelColor;
@property (nonatomic, strong) UIColor *currentPanelCenterColor;
@property (nonatomic, strong) UIColor *currentPromptColor;
@property (nonatomic, strong) UIColor *startPanelColor;
@property (nonatomic, strong) UIColor *startPanelCenterColor;
@property (nonatomic, strong) UIColor *startPromptColor;
@property (nonatomic, strong) UIColor *targetPanelColor;
@property (nonatomic, strong) UIColor *targetPanelCenterColor;
@property (nonatomic, strong) UIColor *targetPromptColor;
@property (nonatomic, assign) CFTimeInterval colorAnimationStartTimestamp;
@property (nonatomic, assign) BOOL colorAnimationActive;
@property (nonatomic, assign) BOOL cancelMode;
@property (nonatomic, copy) NSString *promptText;
@property (nonatomic, assign) NSUInteger barCount;
@property (nonatomic, assign) float *currentBarProfile;
@property (nonatomic, assign) float *targetBarProfile;
@property (nonatomic, assign) float *currentBarHeights;
@property (nonatomic, assign) NSInteger waveProfileHotspotCount;
@property (nonatomic, assign) CFTimeInterval lastWaveProfileUpdatedAt;
@end

@implementation ZXANewVoiceRecordCanvasView

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self != nil) {
        self.backgroundColor = UIColor.clearColor;
        self.opaque = NO;

        _panelBackgroundColor = ZXAColorFromRGB(0xF6F7F8);
        _normalPanelColor = ZXAColorFromRGB(0x061BFF);
        _normalPanelCenterColor = ZXAColorFromRGB(0x1436FF);
        _cancelPanelColor = ZXAColorFromRGB(0xF4574F);
        _cancelPanelCenterColor = ZXAColorFromRGB(0xFA685F);
        _normalPromptColor = ZXAColorFromRGB(0x2F3237);
        _cancelPromptColor = ZXAColorFromRGB(0xF4574F);
        _currentPanelColor = _normalPanelColor;
        _currentPanelCenterColor = _normalPanelCenterColor;
        _currentPromptColor = _normalPromptColor;
        _promptText = @"松手发送 上移取消";
        _fingerPoint = CGPointZero;
        _targetFingerPoint = CGPointZero;
        _waveProfileHotspotCount = -1;
    }
    return self;
}

- (void)dealloc {
    [self stopAnimating];
    free(_currentBarProfile);
    free(_targetBarProfile);
    free(_currentBarHeights);
}

- (void)reset {
    self.displayedVolume = 0.0;
    self.targetVolume = 0.0;
    self.wavePhase = 0.0;
    self.cancelMode = NO;
    self.promptText = @"松手发送 上移取消";
    self.currentPanelColor = self.normalPanelColor;
    self.currentPanelCenterColor = self.normalPanelCenterColor;
    self.currentPromptColor = self.normalPromptColor;
    self.colorAnimationActive = NO;
    self.fingerPoint = CGPointMake(CGRectGetMidX(self.bounds), CGRectGetMaxY(self.bounds));
    self.targetFingerPoint = self.fingerPoint;
    [self resetVisualizerProfile];
    [self setNeedsDisplay];
}

- (void)startAnimating {
    if (self.displayLink != nil) {
        return;
    }
    self.animationStartTimestamp = 0.0;
    self.displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(displayLinkTick:)];
    [self.displayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
}

- (void)stopAnimating {
    [self.displayLink invalidate];
    self.displayLink = nil;
    self.colorAnimationActive = NO;
}

- (void)updateVolume:(NSInteger)volume {
    self.targetVolume = ZXAClamp(volume, ZXAMinVolume, ZXAMaxVolume);
}

- (void)updateFinger:(CGPoint)point active:(BOOL)active {
    self.targetFingerPoint = CGPointMake(
        ZXAClamp(point.x, 0.0, CGRectGetWidth(self.bounds)),
        ZXAClamp(point.y, 0.0, CGRectGetHeight(self.bounds))
    );
    if (CGPointEqualToPoint(self.fingerPoint, CGPointZero)) {
        self.fingerPoint = self.targetFingerPoint;
    }
}

- (BOOL)setCancelMode:(BOOL)cancel {
    if (self.cancelMode == cancel) {
        return NO;
    }
    self.cancelMode = cancel;
    self.promptText = cancel ? @"松手取消" : @"松手发送 上移取消";
    [self animatePanelColorsToPanelColor:(cancel ? self.cancelPanelColor : self.normalPanelColor)
                         panelCenterColor:(cancel ? self.cancelPanelCenterColor : self.normalPanelCenterColor)
                              promptColor:(cancel ? self.cancelPromptColor : self.normalPromptColor)];
    return YES;
}

- (BOOL)isPointInsideRecordArea:(CGPoint)point {
    if (CGRectIsEmpty(self.bounds)) {
        return NO;
    }
    [self updatePanelBounds];
    return [self isPoint:point insideRoundedRect:self.panelRect radius:MIN(22.0, CGRectGetHeight(self.panelRect) / 2.0)];
}

- (void)drawRect:(CGRect)rect {
    [super drawRect:rect];
    [self updatePanelBounds];
    [self drawPanelBackground];
    [self drawPrompt];
    [self drawPanel];
    [self drawVisualizer];
}

- (void)displayLinkTick:(CADisplayLink *)displayLink {
    if (self.animationStartTimestamp <= 0.0) {
        self.animationStartTimestamp = displayLink.timestamp;
    }
    CFTimeInterval elapsed = displayLink.timestamp - self.animationStartTimestamp;
    self.wavePhase = fmod(elapsed, ZXAWaveDurationSeconds) / ZXAWaveDurationSeconds;
    self.displayedVolume += (self.targetVolume - self.displayedVolume) * ZXAVolumeEasing;
    self.fingerPoint = CGPointMake(
        self.fingerPoint.x + (self.targetFingerPoint.x - self.fingerPoint.x) * ZXAFingerEasing,
        self.fingerPoint.y + (self.targetFingerPoint.y - self.fingerPoint.y) * ZXAFingerEasing
    );
    [self updateAnimatedColorsWithTimestamp:displayLink.timestamp];
    [self setNeedsDisplay];
}

- (void)updateAnimatedColorsWithTimestamp:(CFTimeInterval)timestamp {
    if (!self.colorAnimationActive) {
        return;
    }
    CGFloat progress = ZXAClamp((timestamp - self.colorAnimationStartTimestamp) / ZXAColorAnimationDurationSeconds, 0.0, 1.0);
    CGFloat easedProgress = 1.0 - pow(1.0 - progress, 2.0);
    self.currentPanelColor = ZXAInterpolateColor(self.startPanelColor, self.targetPanelColor, easedProgress);
    self.currentPanelCenterColor = ZXAInterpolateColor(self.startPanelCenterColor, self.targetPanelCenterColor, easedProgress);
    self.currentPromptColor = ZXAInterpolateColor(self.startPromptColor, self.targetPromptColor, easedProgress);
    if (progress >= 1.0) {
        self.colorAnimationActive = NO;
    }
}

- (void)drawPanelBackground {
    CGContextRef context = UIGraphicsGetCurrentContext();
    if (context == nil) {
        return;
    }

    CGFloat promptTop = [self promptTopY];
    CGFloat solidBackgroundTop = MAX(0.0, promptTop - 10.0);
    CGFloat fadeTop = MAX(0.0, solidBackgroundTop - 5.0);

    if (fadeTop < solidBackgroundTop) {
        UIColor *transparent = ZXAColorWithAlpha(self.panelBackgroundColor, 0.0);
        NSArray *colors = @[(__bridge id)transparent.CGColor, (__bridge id)self.panelBackgroundColor.CGColor];
        CGFloat locations[] = {0.0, 1.0};
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGGradientRef gradient = CGGradientCreateWithColors(colorSpace, (__bridge CFArrayRef)colors, locations);
        CGContextDrawLinearGradient(context, gradient, CGPointMake(0.0, fadeTop), CGPointMake(0.0, solidBackgroundTop), 0);
        CGGradientRelease(gradient);
        CGColorSpaceRelease(colorSpace);
    }

    [self.panelBackgroundColor setFill];
    UIRectFill(CGRectMake(0.0, solidBackgroundTop, CGRectGetWidth(self.bounds), CGRectGetHeight(self.bounds) - solidBackgroundTop));
}

- (void)drawPrompt {
    UIFont *font = [UIFont systemFontOfSize:16.0 weight:UIFontWeightRegular];
    NSMutableParagraphStyle *style = [[NSMutableParagraphStyle alloc] init];
    style.alignment = NSTextAlignmentCenter;
    NSDictionary *attributes = @{
        NSFontAttributeName: font,
        NSForegroundColorAttributeName: self.currentPromptColor,
        NSParagraphStyleAttributeName: style
    };
    CGFloat centerY = [self promptCenterY];
    CGFloat lineHeight = font.lineHeight;
    CGRect textRect = CGRectMake(0.0, centerY - lineHeight / 2.0, CGRectGetWidth(self.bounds), lineHeight);
    [self.promptText drawInRect:textRect withAttributes:attributes];
}

- (void)drawPanel {
    CGContextRef context = UIGraphicsGetCurrentContext();
    if (context == nil) {
        return;
    }

    CGContextSaveGState(context);
    UIBezierPath *path = [UIBezierPath bezierPathWithRoundedRect:self.panelRect cornerRadius:MIN(22.0, CGRectGetHeight(self.panelRect) / 2.0)];
    [path addClip];
    NSArray *colors = @[
        (__bridge id)self.currentPanelColor.CGColor,
        (__bridge id)self.currentPanelCenterColor.CGColor,
        (__bridge id)self.currentPanelColor.CGColor
    ];
    CGFloat locations[] = {0.0, 0.52, 1.0};
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    CGGradientRef gradient = CGGradientCreateWithColors(colorSpace, (__bridge CFArrayRef)colors, locations);
    CGPoint start = CGPointMake(CGRectGetMinX(self.panelRect), CGRectGetMidY(self.panelRect));
    CGPoint end = CGPointMake(CGRectGetMaxX(self.panelRect), CGRectGetMidY(self.panelRect));
    CGContextDrawLinearGradient(context, gradient, start, end, 0);
    CGGradientRelease(gradient);
    CGColorSpaceRelease(colorSpace);
    CGContextRestoreGState(context);
}

- (void)drawVisualizer {
    CGFloat availableWidth = CGRectGetWidth(self.panelRect) * ZXAVisualizerWidthRatio;
    CGFloat visualizerWidth = MIN(280.0, availableWidth);
    CGFloat barWidth = 2.0;
    CGFloat barGap = 3.0;
    CGFloat slotWidth = barWidth + barGap;
    NSInteger barCount = MAX(ZXAMinBarCount, (NSInteger)(visualizerWidth / slotWidth));
    CGFloat totalWidth = barCount * barWidth + (barCount - 1) * barGap;
    CGFloat startX = CGRectGetMidX(self.panelRect) - totalWidth / 2.0;
    CGFloat centerIndex = (barCount - 1) / 2.0;
    CGFloat volumeRatio = ZXAClamp(self.displayedVolume / ZXAMaxVolume, 0.0, 1.0);
    CGFloat pulse = (sin(self.wavePhase * ZXAFullCircle) + 1.0) / 2.0;
    CGFloat baselineY = CGRectGetMidY(self.panelRect);
    [self updateRandomWaveProfileWithBarCount:barCount volumeRatio:volumeRatio];

    for (NSInteger index = 0; index < barCount; index++) {
        CGFloat distanceFromCenter = centerIndex == 0.0 ? 0.0 : fabs(index - centerIndex) / centerIndex;
        CGFloat envelope = ZXAClamp(1.0 - distanceFromCenter * 0.72, 0.24, 1.0);
        CGFloat randomBarProfile = self.barCount == (NSUInteger)barCount ? self.currentBarProfile[index] : 0.0;
        CGFloat amplifiedVolumeRatio = ZXAClamp(volumeRatio * ZXAVisualizerVolumeAmplitudeMultiplier, 0.0, 1.0);
        CGFloat targetHeight = 3.0
            + (22.0 - 3.0)
            * (0.12
               + volumeRatio * 0.08 * envelope
               + amplifiedVolumeRatio
                    * (0.46 + 0.54 * pulse)
                    * randomBarProfile
                    * envelope);
        CGFloat activeHeight = [self updateDisplayedBarHeightAtIndex:index targetHeight:targetHeight];
        CGFloat x = startX + index * slotWidth;
        CGFloat top = baselineY - activeHeight / 2.0;
        CGFloat alpha = ZXAClamp((150.0 + 105.0 * envelope * (0.35 + volumeRatio * 0.65)) / 255.0, 140.0 / 255.0, 1.0);
        CGRect barRect = CGRectMake(x, top, barWidth, activeHeight);
        [[UIColor colorWithWhite:1.0 alpha:alpha] setFill];
        [[UIBezierPath bezierPathWithRoundedRect:barRect cornerRadius:2.0] fill];
    }
}

- (CGFloat)updateDisplayedBarHeightAtIndex:(NSInteger)index targetHeight:(CGFloat)targetHeight {
    if (index < 0 || (NSUInteger)index >= self.barCount) {
        return targetHeight;
    }
    CGFloat currentHeight = self.currentBarHeights[index];
    currentHeight += (targetHeight - currentHeight) * ZXAVisualizerBarHeightEasing;
    self.currentBarHeights[index] = currentHeight;
    return currentHeight;
}

- (void)updateRandomWaveProfileWithBarCount:(NSInteger)barCount volumeRatio:(CGFloat)volumeRatio {
    BOOL forceRefresh = [self ensureVisualizerProfileSize:barCount];
    NSInteger hotspotCount = [self resolveWaveProfileHotspotCountWithBarCount:barCount volumeRatio:volumeRatio];
    CFTimeInterval now = CACurrentMediaTime();
    if (forceRefresh
        || hotspotCount != self.waveProfileHotspotCount
        || now - self.lastWaveProfileUpdatedAt >= ZXARandomWaveProfileIntervalSeconds) {
        [self generateRandomWaveProfileWithBarCount:barCount hotspotCount:hotspotCount];
        self.waveProfileHotspotCount = hotspotCount;
        self.lastWaveProfileUpdatedAt = now;
    }

    for (NSInteger index = 0; index < barCount; index++) {
        self.currentBarProfile[index] += (self.targetBarProfile[index] - self.currentBarProfile[index])
            * ZXARandomWaveProfileEasing;
    }
}

- (BOOL)ensureVisualizerProfileSize:(NSInteger)barCount {
    if (self.barCount == (NSUInteger)barCount
        && self.currentBarProfile != NULL
        && self.targetBarProfile != NULL
        && self.currentBarHeights != NULL) {
        return NO;
    }

    free(self.currentBarProfile);
    free(self.targetBarProfile);
    free(self.currentBarHeights);
    self.barCount = (NSUInteger)barCount;
    self.currentBarProfile = calloc(self.barCount, sizeof(float));
    self.targetBarProfile = calloc(self.barCount, sizeof(float));
    self.currentBarHeights = calloc(self.barCount, sizeof(float));
    [self resetDisplayedBarHeights];
    self.waveProfileHotspotCount = -1;
    self.lastWaveProfileUpdatedAt = 0.0;
    return YES;
}

- (void)resetVisualizerProfile {
    if (self.currentBarProfile != NULL) {
        memset(self.currentBarProfile, 0, self.barCount * sizeof(float));
    }
    if (self.targetBarProfile != NULL) {
        memset(self.targetBarProfile, 0, self.barCount * sizeof(float));
    }
    [self resetDisplayedBarHeights];
    self.waveProfileHotspotCount = -1;
    self.lastWaveProfileUpdatedAt = 0.0;
}

- (void)resetDisplayedBarHeights {
    if (self.currentBarHeights == NULL) {
        return;
    }
    for (NSUInteger index = 0; index < self.barCount; index++) {
        self.currentBarHeights[index] = 3.0;
    }
}

- (NSInteger)resolveWaveProfileHotspotCountWithBarCount:(NSInteger)barCount volumeRatio:(CGFloat)volumeRatio {
    if (volumeRatio < ZXARandomWaveSilenceThreshold) {
        return 0;
    }
    NSInteger maxHotspotCount = MIN(ZXARandomWaveMaxHotspots, MAX(1, barCount / 3));
    CGFloat responsiveVolumeRatio = pow(volumeRatio, ZXARandomWaveCountVolumePower);
    return ZXAClampInteger(1 + lround((maxHotspotCount - 1) * responsiveVolumeRatio), 1, maxHotspotCount);
}

- (void)generateRandomWaveProfileWithBarCount:(NSInteger)barCount hotspotCount:(NSInteger)hotspotCount {
    if (self.targetBarProfile == NULL) {
        return;
    }
    for (NSInteger index = 0; index < barCount; index++) {
        self.targetBarProfile[index] = 0.0;
    }
    for (NSInteger hotspotIndex = 0; hotspotIndex < hotspotCount; hotspotIndex++) {
        CGFloat center = [self pickCenterBiasedBarIndex:barCount];
        CGFloat span = ZXARandomWaveMinBarSpan + ZXARandomUnit() * (ZXARandomWaveMaxBarSpan - ZXARandomWaveMinBarSpan);
        CGFloat strength = ZXARandomWaveMinStrength + ZXARandomUnit() * (ZXARandomWaveMaxStrength - ZXARandomWaveMinStrength);
        CGFloat shapePower = ZXARandomWaveMinShapePower + ZXARandomUnit() * (ZXARandomWaveMaxShapePower - ZXARandomWaveMinShapePower);
        NSInteger startIndex = MAX(0, (NSInteger)floor(center - span));
        NSInteger endIndex = MIN(barCount - 1, (NSInteger)ceil(center + span));
        for (NSInteger index = startIndex; index <= endIndex; index++) {
            CGFloat distanceRatio = fabs(index - center) / span;
            CGFloat falloff = 1.0 - ZXAClamp(distanceRatio, 0.0, 1.0);
            CGFloat profile = strength * pow(falloff, shapePower);
            self.targetBarProfile[index] = MAX(self.targetBarProfile[index], profile);
        }
    }
}

- (CGFloat)pickCenterBiasedBarIndex:(NSInteger)barCount {
    if (barCount <= 1) {
        return 0.0;
    }
    CGFloat maxIndex = barCount - 1.0;
    CGFloat centerIndex = maxIndex / 2.0;
    if (ZXARandomUnit() < ZXARandomWaveCenterBiasProbability) {
        CGFloat offset = ZXARandomGaussian() * barCount * ZXARandomWaveCenterStandardDeviationRatio;
        return ZXAClamp(centerIndex + offset, 0.0, maxIndex);
    }
    return ZXARandomUnit() * maxIndex;
}

- (void)animatePanelColorsToPanelColor:(UIColor *)panelColor
                      panelCenterColor:(UIColor *)panelCenterColor
                           promptColor:(UIColor *)promptColor {
    self.startPanelColor = self.currentPanelColor;
    self.startPanelCenterColor = self.currentPanelCenterColor;
    self.startPromptColor = self.currentPromptColor;
    self.targetPanelColor = panelColor;
    self.targetPanelCenterColor = panelCenterColor;
    self.targetPromptColor = promptColor;
    self.colorAnimationStartTimestamp = CACurrentMediaTime();
    self.colorAnimationActive = YES;
}

- (void)updatePanelBounds {
    CGFloat left = 15.0;
    CGFloat right = CGRectGetWidth(self.bounds) - 15.0;
    CGFloat bottom = [self resolvePanelBottom];
    self.panelRect = CGRectMake(left, bottom - 44.0, right - left, 44.0);
}

- (CGFloat)resolvePanelBottom {
    UIView *anchorView = self.panelAnchorView;
    if (anchorView == nil
        || anchorView.window == nil
        || self.window == nil
        || CGRectGetWidth(anchorView.bounds) <= 0.0
        || CGRectGetHeight(anchorView.bounds) <= 0.0
        || CGRectGetHeight(self.bounds) <= 0.0) {
        return CGRectGetHeight(self.bounds) - 31.0;
    }

    CGPoint anchorCenter = CGPointMake(CGRectGetMidX(anchorView.bounds), CGRectGetMidY(anchorView.bounds));
    CGPoint convertedCenter = [anchorView convertPoint:anchorCenter toView:self];
    CGFloat top = convertedCenter.y - 44.0 / 2.0;
    CGFloat clampedTop = ZXAClamp(top, 0.0, MAX(0.0, CGRectGetHeight(self.bounds) - 44.0));
    return clampedTop + 44.0;
}

- (CGFloat)promptCenterY {
    return CGRectGetMinY(self.panelRect) - 26.0;
}

- (CGFloat)promptTopY {
    UIFont *font = [UIFont systemFontOfSize:16.0 weight:UIFontWeightRegular];
    return [self promptCenterY] - font.lineHeight / 2.0;
}

- (BOOL)isPoint:(CGPoint)point insideRoundedRect:(CGRect)rect radius:(CGFloat)radius {
    if (!CGRectContainsPoint(rect, point)) {
        return NO;
    }
    CGFloat leftCenterX = CGRectGetMinX(rect) + radius;
    CGFloat rightCenterX = CGRectGetMaxX(rect) - radius;
    CGFloat topCenterY = CGRectGetMinY(rect) + radius;
    CGFloat bottomCenterY = CGRectGetMaxY(rect) - radius;
    CGFloat checkX = ZXAClamp(point.x, leftCenterX, rightCenterX);
    CGFloat checkY = ZXAClamp(point.y, topCenterY, bottomCenterY);
    CGFloat dx = point.x - checkX;
    CGFloat dy = point.y - checkY;
    return dx * dx + dy * dy <= radius * radius;
}

@end

@interface ZXANewVoiceRecordPanel ()
@property (nonatomic, strong) ZXANewVoiceRecordCanvasView *canvasView;
@property (nonatomic, strong) ZXANewVoiceAmplitudeRecorder *recorder;
@property (nonatomic, strong) NSMapTable<UIView *, ZXAVoiceHoldGestureRecognizer *> *recognizersByTrigger;
@property (nonatomic, weak, nullable) UIView *lastHostView;
@property (nonatomic, assign) BOOL pendingShowAfterPermission;
@property (nonatomic, assign) BOOL holdTriggered;
@property (nonatomic, assign) BOOL recording;
@property (nonatomic, assign) BOOL ending;
@end

@implementation ZXANewVoiceRecordPanel

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self != nil) {
        [self initializePanel];
    }
    return self;
}

- (instancetype)initWithCoder:(NSCoder *)coder {
    self = [super initWithCoder:coder];
    if (self != nil) {
        [self initializePanel];
    }
    return self;
}

- (void)initializePanel {
    self.backgroundColor = UIColor.clearColor;
    self.opaque = NO;
    self.userInteractionEnabled = YES;
    self.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.canvasView = [[ZXANewVoiceRecordCanvasView alloc] initWithFrame:self.bounds];
    self.canvasView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self addSubview:self.canvasView];
    self.recorder = [[ZXANewVoiceAmplitudeRecorder alloc] init];
    self.recognizersByTrigger = [NSMapTable weakToStrongObjectsMapTable];
}

- (void)setRecordPanelAnchorView:(UIView *)recordPanelAnchorView {
    _recordPanelAnchorView = recordPanelAnchorView;
    self.canvasView.panelAnchorView = recordPanelAnchorView;
    [self.canvasView setNeedsDisplay];
}

- (void)showInView:(UIView *)view {
    if (self.superview != nil || self.recording || self.ending || view == nil) {
        return;
    }

    self.lastHostView = view;
    AVAudioSessionRecordPermission permission = AVAudioSession.sharedInstance.recordPermission;
    if (permission == AVAudioSessionRecordPermissionUndetermined) {
        self.pendingShowAfterPermission = YES;
        __weak typeof(self) weakSelf = self;
        [AVAudioSession.sharedInstance requestRecordPermission:^(BOOL granted) {
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong typeof(weakSelf) strongSelf = weakSelf;
                if (strongSelf == nil || !strongSelf.pendingShowAfterPermission) {
                    return;
                }
                strongSelf.pendingShowAfterPermission = NO;
                if (granted) {
                    [strongSelf attachAndStartInView:strongSelf.lastHostView];
                } else {
                    [strongSelf notifyCancel];
                }
            });
        }];
        return;
    }

    if (permission != AVAudioSessionRecordPermissionGranted) {
        [self notifyCancel];
        return;
    }

    [self attachAndStartInView:view];
}

- (void)dismiss {
    self.pendingShowAfterPermission = NO;
    [self releaseRecording];
    [self removeFromSuperview];
}

- (void)bindToHoldTrigger:(UIView *)trigger {
    [self bindTrigger:trigger startImmediately:NO preserveTap:NO condition:nil];
}

- (void)bindToImmediateHoldTrigger:(UIView *)trigger {
    [self bindTrigger:trigger startImmediately:YES preserveTap:NO condition:nil];
}

- (void)bindToHoldTriggerPreservingTapWhen:(UIView *)trigger
                                 condition:(ZXAVoiceRecordHoldCondition)condition {
    [self bindTrigger:trigger startImmediately:NO preserveTap:YES condition:condition];
}

- (void)bindTrigger:(UIView *)trigger
   startImmediately:(BOOL)startImmediately
        preserveTap:(BOOL)preserveTap
          condition:(ZXAVoiceRecordHoldCondition)condition {
    if (trigger == nil) {
        return;
    }
    ZXAVoiceHoldGestureRecognizer *oldRecognizer = [self.recognizersByTrigger objectForKey:trigger];
    if (oldRecognizer != nil) {
        [trigger removeGestureRecognizer:oldRecognizer];
    }

    trigger.userInteractionEnabled = YES;
    ZXAVoiceHoldGestureRecognizer *recognizer =
        [[ZXAVoiceHoldGestureRecognizer alloc] initWithTarget:self action:@selector(handleHoldGesture:)];
    recognizer.minimumPressDuration = startImmediately ? 0.0 : ZXAHoldStartDelaySeconds;
    recognizer.allowableMovement = CGFLOAT_MAX;
    recognizer.cancelsTouchesInView = !preserveTap;
    recognizer.delaysTouchesBegan = NO;
    recognizer.startImmediately = startImmediately;
    recognizer.condition = condition;
    [trigger addGestureRecognizer:recognizer];
    [self.recognizersByTrigger setObject:recognizer forKey:trigger];
}

- (void)handleHoldGesture:(ZXAVoiceHoldGestureRecognizer *)recognizer {
    UIView *trigger = recognizer.view;
    UIView *hostView = trigger.window;
    if (hostView == nil) {
        hostView = self.lastHostView;
    }
    if (hostView == nil) {
        hostView = trigger.superview;
    }
    if (hostView == nil) {
        return;
    }

    switch (recognizer.state) {
        case UIGestureRecognizerStateBegan: {
            BOOL alreadyHandlingHold = self.recording || self.holdTriggered;
            if (!alreadyHandlingHold && recognizer.condition != nil && !recognizer.condition()) {
                return;
            }
            self.holdTriggered = YES;
            [self setTrigger:trigger pressed:YES];
            [self showInView:hostView];
            if (self.superview != nil) {
                [self updateFingerFromGesture:recognizer vibrateOnModeChange:NO];
            }
            break;
        }
        case UIGestureRecognizerStateChanged:
            if (self.recording) {
                [self updateFingerFromGesture:recognizer vibrateOnModeChange:YES];
            }
            break;
        case UIGestureRecognizerStateEnded:
        case UIGestureRecognizerStateCancelled:
        case UIGestureRecognizerStateFailed: {
            BOOL shouldCancel = self.recording ? ![self updateFingerFromGesture:recognizer vibrateOnModeChange:NO] : YES;
            [self setTrigger:trigger pressed:NO];
            if (self.recording) {
                [self finishRecordingCancelled:shouldCancel];
            } else {
                self.pendingShowAfterPermission = NO;
            }
            self.holdTriggered = NO;
            break;
        }
        default:
            break;
    }
}

- (void)attachAndStartInView:(UIView *)view {
    if (view == nil || self.superview != nil) {
        return;
    }
    self.frame = view.bounds;
    [view addSubview:self];
    [view bringSubviewToFront:self];

    [self.canvasView reset];
    [self.canvasView startAnimating];
    [self.canvasView updateFinger:CGPointMake(CGRectGetMidX(self.bounds), CGRectGetMaxY(self.bounds)) active:YES];

    __weak typeof(self) weakSelf = self;
    self.recording = [self.recorder startWithVolumeChangedHandler:^(NSInteger volume) {
        [weakSelf.canvasView updateVolume:volume];
    }];

    if (self.recording) {
        [self vibrateForVoicePanelFeedback];
        if ([self.delegate respondsToSelector:@selector(voiceRecordPanelDidStart:)]) {
            [self.delegate voiceRecordPanelDidStart:self];
        }
    } else {
        [self.canvasView stopAnimating];
        [self removeFromSuperview];
        [self notifyCancel];
    }
}

- (void)finishRecordingCancelled:(BOOL)cancelled {
    if (self.ending) {
        return;
    }
    self.ending = YES;
    BOOL wasRecording = self.recording;
    [self releaseRecording];
    [self removeFromSuperview];
    self.ending = NO;

    if (!wasRecording) {
        return;
    }
    if (cancelled) {
        [self notifyCancel];
    } else if ([self.delegate respondsToSelector:@selector(voiceRecordPanelDidFinish:)]) {
        [self.delegate voiceRecordPanelDidFinish:self];
    }
}

- (void)releaseRecording {
    if (self.recording) {
        [self.recorder stop];
    }
    self.recording = NO;
    [self.canvasView stopAnimating];
    [self.canvasView reset];
}

- (BOOL)updateFingerFromGesture:(UIGestureRecognizer *)recognizer
            vibrateOnModeChange:(BOOL)vibrateOnModeChange {
    if (self.superview == nil) {
        return NO;
    }
    CGPoint point = [recognizer locationInView:self];
    BOOL insideRecordArea = [self.canvasView isPointInsideRecordArea:point];
    [self.canvasView updateFinger:point active:insideRecordArea];
    BOOL cancelModeChanged = [self.canvasView setCancelMode:!insideRecordArea];
    if (vibrateOnModeChange && cancelModeChanged) {
        [self vibrateForVoicePanelFeedback];
    }
    return insideRecordArea;
}

- (void)setTrigger:(UIView *)trigger pressed:(BOOL)pressed {
    if ([trigger isKindOfClass:UIControl.class]) {
        ((UIControl *)trigger).highlighted = pressed;
    }
}

- (void)vibrateForVoicePanelFeedback {
    if (@available(iOS 10.0, *)) {
        UIImpactFeedbackGenerator *generator = [[UIImpactFeedbackGenerator alloc] initWithStyle:UIImpactFeedbackStyleLight];
        [generator prepare];
        [generator impactOccurred];
    } else {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate);
    }
}

- (void)notifyCancel {
    if ([self.delegate respondsToSelector:@selector(voiceRecordPanelDidCancel:)]) {
        [self.delegate voiceRecordPanelDidCancel:self];
    }
}

@end
