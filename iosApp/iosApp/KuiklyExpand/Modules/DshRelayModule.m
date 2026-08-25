#import "DshRelayModule.h"

#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import "iosApp-Swift.h"

@implementation DshRelayModule {
    NSUInteger _listenerToken;
}

@synthesize hr_rootView;

- (void)dealloc {
    if (_listenerToken != 0) {
        [[DshRelayRuntime shared] removeListener:_listenerToken];
    }
}

- (void)scanAndPair:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    UIViewController *presenter = [DshNativeUi topViewController];
    if (!presenter) {
        if (callback) callback(@{ @"ok": @NO, @"message": @"无法打开扫码页" });
        return;
    }
    DshQrScanViewController *scanner = [[DshQrScanViewController alloc] init];
    scanner.modalPresentationStyle = UIModalPresentationFullScreen;
    scanner.onResult = ^(NSString *qr) {
        if (qr.length == 0) {
            if (callback) callback(@{ @"ok": @NO, @"message": @"已取消扫码" });
            return;
        }
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
            NSDictionary *result = [[DshRelayRuntime shared] pairFromQr:qr];
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) callback(result);
            });
        });
    };
    [presenter presentViewController:scanner animated:YES completion:nil];
}

- (void)connect:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    [self listen:callback];
    [[DshRelayRuntime shared] connect];
}

- (void)disconnect:(NSDictionary *)args {
    [[DshRelayRuntime shared] disconnect];
}

- (void)forget:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    [[DshRelayRuntime shared] forgetPairing];
    if (callback) callback(@{ @"ok": @YES });
}

- (id)status:(NSDictionary *)args {
    return [[DshRelayRuntime shared] currentState];
}

- (void)subscribe:(NSDictionary *)args {
    [self listen:args[KR_CALLBACK_KEY]];
}

- (void)listen:(KuiklyRenderCallback)callback {
    if (_listenerToken != 0) {
        [[DshRelayRuntime shared] removeListener:_listenerToken];
        _listenerToken = 0;
    }
    if (!callback) return;
    _listenerToken = [[DshRelayRuntime shared] addListener:^(NSDictionary *state) {
        callback(state);
    }];
}

@end
