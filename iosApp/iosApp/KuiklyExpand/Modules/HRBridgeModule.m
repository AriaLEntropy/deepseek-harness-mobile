#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import "iosApp-Swift.h"

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

// ===== 崩溃捕获（加分项）：写 Documents/last_crash.txt，下次启动由日志中心读取提示 =====
static NSString *DshLastCrashPath(void) {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    return [paths.firstObject stringByAppendingPathComponent:@"last_crash.txt"];
}

static NSUncaughtExceptionHandler *DshPreviousExceptionHandler = NULL;

static void DshUncaughtExceptionHandler(NSException *exception) {
    NSDictionary *info = [[NSBundle mainBundle] infoDictionary];
    NSString *content = [NSString stringWithFormat:
        @"time=%lld\nversion=%@\ndevice=%@ / iOS %@\nthread=%@\nreason=%@\nstack=%@",
        (long long)([[NSDate date] timeIntervalSince1970] * 1000.0),
        info[@"CFBundleShortVersionString"] ?: @"",
        [[UIDevice currentDevice] model],
        [[UIDevice currentDevice] systemVersion],
        [NSThread currentThread].name ?: @"main",
        exception.reason ?: @"",
        [[exception callStackSymbols] componentsJoinedByString:@"\n"]];
    [content writeToFile:DshLastCrashPath() atomically:YES encoding:NSUTF8StringEncoding error:nil];
    if (DshPreviousExceptionHandler) {
        DshPreviousExceptionHandler(exception);
    }
}

@implementation HRBridgeModule

+ (void)load {
    DshPreviousExceptionHandler = NSGetUncaughtExceptionHandler();
    NSSetUncaughtExceptionHandler(&DshUncaughtExceptionHandler);
}

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    if (content.length == 0) return;
    [DshNativeUi toast:content];
}

- (void)shareExportFile:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *path = params[@"path"];
    if (path.length == 0 || ![[NSFileManager defaultManager] fileExistsAtPath:path]) {
        if (callback) callback(@{ @"ok": @NO, @"message": @"导出文件不存在" });
        return;
    }
    NSURL *url = [NSURL fileURLWithPath:path];
    UIViewController *presenter = [DshNativeUi topViewController];
    if (!presenter) {
        if (callback) callback(@{ @"ok": @NO, @"message": @"无法打开分享面板" });
        return;
    }
    UIActivityViewController *activityVC =
        [[UIActivityViewController alloc] initWithActivityItems:@[url] applicationActivities:nil];
    // iPad 需要 popover 锚点，否则会崩溃
    activityVC.popoverPresentationController.sourceView = presenter.view;
    activityVC.popoverPresentationController.sourceRect = CGRectMake(CGRectGetMidX(presenter.view.bounds), CGRectGetMidY(presenter.view.bounds), 0, 0);
    [presenter presentViewController:activityVC animated:YES completion:^{
        if (callback) callback(@{ @"ok": @YES, @"message": @"分享面板已打开" });
    }];
}

- (NSString *)readLastCrash:(NSDictionary *)args {
    NSString *path = DshLastCrashPath();
    if (![[NSFileManager defaultManager] fileExistsAtPath:path]) return @"";
    return [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil] ?: @"";
}

- (void)clearLastCrash:(NSDictionary *)args {
    [[NSFileManager defaultManager] removeItemAtPath:DshLastCrashPath() error:nil];
}

- (NSString *)getDeviceInfo:(NSDictionary *)args {
    NSDictionary *info = [[NSBundle mainBundle] infoDictionary];
    NSDictionary *device = @{
        @"version": info[@"CFBundleShortVersionString"] ?: @"",
        @"model": [NSString stringWithFormat:@"%@ / iOS %@", [[UIDevice currentDevice] model], [[UIDevice currentDevice] systemVersion]],
        @"os": [NSString stringWithFormat:@"iOS %@", [[UIDevice currentDevice] systemVersion]]
    };
    NSData *data = [NSJSONSerialization dataWithJSONObject:device options:0 error:nil];
    return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

- (NSString *)closeKeyboard:(NSDictionary *)args {
    void (^dismissKeyboard)(void) = ^{
        [self.hr_rootView endEditing:YES];
        [self.hr_rootView.window endEditing:YES];
    };
    if ([NSThread isMainThread]) {
        dismissKeyboard();
    } else {
        dispatch_sync(dispatch_get_main_queue(), dismissKeyboard);
    }
    return @"true";
}

- (void)setSystemBarsDimmed:(NSDictionary *)args {
}

- (void)pickSshKey:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    UIViewController *presenter = [DshNativeUi topViewController];
    if (!presenter) {
        if (callback) callback(@{ @"uri": @"" });
        return;
    }
    [[DshSshKeyStore shared] pickKeyFrom:presenter completion:^(NSString *uri) {
        if (callback) callback(@{ @"uri": uri ?: @"" });
    }];
}

- (void)importSshKey:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *keyId = [[DshSshKeyStore shared] importUri:params[@"uri"] ?: @""];
    if (keyId.length == 0) {
        if (callback) callback(@{ @"ok": @NO, @"message": @"无法读取 SSH 私钥" });
        return;
    }
    if (callback) callback(@{ @"ok": @YES, @"keyId": keyId });
}

- (void)validateSshKey:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    BOOL valid = [[DshSshKeyStore shared] validateKey:params[@"keyId"] ?: @""];
    if (callback) callback(@{ @"valid": @(valid) });
}

- (void)deleteSshKey:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    [[DshSshKeyStore shared] deleteKey:params[@"keyId"] ?: @""];
}

- (void)startSshKeepAlive:(NSDictionary *)args {
}

- (void)stopSshKeepAlive:(NSDictionary *)args {
}

@end
