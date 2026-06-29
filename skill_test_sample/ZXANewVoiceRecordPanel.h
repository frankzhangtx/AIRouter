#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class ZXANewVoiceRecordPanel;

@protocol ZXANewVoiceRecordPanelDelegate <NSObject>
@optional
- (void)voiceRecordPanelDidStart:(ZXANewVoiceRecordPanel *)panel;
- (void)voiceRecordPanelDidCancel:(ZXANewVoiceRecordPanel *)panel;
- (void)voiceRecordPanelDidFinish:(ZXANewVoiceRecordPanel *)panel;
@end

typedef BOOL (^ZXAVoiceRecordHoldCondition)(void);

@interface ZXANewVoiceRecordPanel : UIView

@property (nonatomic, weak, nullable) id<ZXANewVoiceRecordPanelDelegate> delegate;
@property (nonatomic, weak, nullable) UIView *recordPanelAnchorView;

- (void)showInView:(UIView *)view;
- (void)dismiss;

- (void)bindToHoldTrigger:(UIView *)trigger;
- (void)bindToImmediateHoldTrigger:(UIView *)trigger;
- (void)bindToHoldTriggerPreservingTapWhen:(UIView *)trigger
                                 condition:(nullable ZXAVoiceRecordHoldCondition)condition;

@end

NS_ASSUME_NONNULL_END
