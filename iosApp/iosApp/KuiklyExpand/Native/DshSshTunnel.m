#import "DshSshTunnel.h"

#import <CommonCrypto/CommonDigest.h>
#import <arpa/inet.h>
#import <fcntl.h>
#import <netinet/in.h>
#import <netinet/tcp.h>
#import <sys/socket.h>
#import <unistd.h>

#if __has_include(<NMSSH/NMSSH.h>)
#import <NMSSH/NMSSH.h>
#else
#import "NMSSH.h"
#endif

#if __has_include(<NMSSH/libssh2.h>)
#import <NMSSH/libssh2.h>
#elif __has_include(<libssh2.h>)
#import <libssh2.h>
#elif __has_include("libssh2.h")
#import "libssh2.h"
#endif

static const NSTimeInterval kConnectTimeout = 10;
static const int kKeepAliveSeconds = 15;

@interface DshSshConn : NSObject
@property (nonatomic, assign) int fd;
@property (nonatomic, assign) LIBSSH2_CHANNEL *channel;
@end

@implementation DshSshConn
@end

@implementation DshSshTunnel {
    NSString *_host;
    NSInteger _port;
    NSString *_username;
    NSInteger _remoteDshPort;
    NSData *_keyBytes;
    NSString *_passphrase;
    NSString *_acceptedFingerprint;
    int _listenFd;
    int _localPort;
    BOOL _stopped;
    BOOL _connecting;
    int64_t _generation;
    NSLock *_lock;
    NSThread *_pumpThread;
    NSThread *_reconnectThread;
    NMSSHSession *_session;
    NSMutableArray<DshSshConn *> *_conns;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _listenFd = -1;
        _lock = [[NSLock alloc] init];
        _stopped = YES;
        _passphrase = @"";
        _acceptedFingerprint = @"";
        _conns = [NSMutableArray array];
    }
    return self;
}

- (void)connectWithHost:(NSString *)host
                   port:(NSInteger)port
               username:(NSString *)username
          remoteDshPort:(NSInteger)remoteDshPort
               keyBytes:(NSData *)keyBytes
             passphrase:(NSString *)passphrase
            fingerprint:(NSString *)fingerprint {
    [_lock lock];
    if (_connecting) {
        [_lock unlock];
        return;
    }
    _connecting = YES;
    _stopped = NO;
    _host = [host copy];
    _port = port;
    _username = [username copy];
    _remoteDshPort = remoteDshPort;
    _keyBytes = [keyBytes copy];
    _passphrase = passphrase ?: @"";
    _acceptedFingerprint = fingerprint ?: @"";
    [_lock unlock];
    [NSThread detachNewThreadWithBlock:^{ [self connectInternal]; }];
}

- (void)acceptFingerprint:(NSString *)fingerprint {
    if (fingerprint.length == 0 || _stopped) return;
    [_lock lock];
    if (_connecting) {
        [_lock unlock];
        return;
    }
    _acceptedFingerprint = [fingerprint copy];
    _connecting = YES;
    [_lock unlock];
    [NSThread detachNewThreadWithBlock:^{ [self connectInternal]; }];
}

- (void)disconnect {
    _stopped = YES;
    [_lock lock];
    _generation += 1;
    _connecting = NO;
    [_lock unlock];
    [self closeListen];
    [self publish:@{
        @"phase": @"STOPPED",
        @"message": @"SSH 已断开",
        @"localPort": @0,
        @"generation": @(_generation),
    }];
}

- (NSString *)endpoint {
    return _localPort > 0 ? [NSString stringWithFormat:@"http://127.0.0.1:%d", _localPort] : nil;
}

- (void)connectInternal {
    [_lock lock];
    _generation += 1;
    int64_t myGeneration = _generation;
    [_lock unlock];
    @try {
        [self closeSession];
        [self publish:@{ @"phase": @"CONNECTING", @"message": @"正在连接 SSH", @"localPort": @0, @"generation": @(myGeneration) }];
        NMSSHSession *session = [[NMSSHSession alloc] initWithHost:_host port:_port andUsername:_username];
        if (![session connectWithTimeout:@(kConnectTimeout)]) {
            NSString *error = session.lastError.localizedDescription ?: @"无法连接 SSH 主机";
            @throw [NSException exceptionWithName:@"DshSshConnect" reason:error userInfo:nil];
        }
        NSString *fingerprint = [self sha256Fingerprint:session];
        NSString *saved = _acceptedFingerprint ?: @"";
        if (saved.length == 0 || ![saved isEqualToString:fingerprint]) {
            [session disconnect];
            [self publish:@{
                @"phase": @"FINGERPRINT_REQUIRED",
                @"message": fingerprint ?: @"",
                @"localPort": @0,
                @"generation": @(myGeneration),
            }];
            return;
        }
        [self publish:@{ @"phase": @"AUTHENTICATING", @"message": @"正在验证 SSH 身份", @"localPort": @0, @"generation": @(myGeneration) }];
        NSString *pem = [[NSString alloc] initWithData:_keyBytes encoding:NSUTF8StringEncoding];
        if (pem.length == 0) {
            [session disconnect];
            @throw [NSException exceptionWithName:@"DshSshAuth" reason:@"SSH 私钥格式无法识别" userInfo:nil];
        }
        NSString *password = _passphrase.length > 0 ? _passphrase : nil;
        BOOL ok = [session authenticateByInMemoryPublicKey:@"" privateKey:pem andPassword:password];
        if (!ok || !session.isAuthorized) {
            NSString *reason = session.lastError.localizedDescription ?: @"SSH 身份验证失败，请检查用户名和私钥";
            [session disconnect];
            @throw [NSException exceptionWithName:@"DshSshAuth" reason:reason userInfo:nil];
        }
        if (_stopped || myGeneration != [self currentGeneration]) {
            [session disconnect];
            return;
        }
        _session = session;
        libssh2_keepalive_config(session.session, 1, kKeepAliveSeconds);
        [self publish:@{ @"phase": @"FORWARDING", @"message": @"正在建立 DSH 转发", @"localPort": @0, @"generation": @(myGeneration) }];
        if (![self startListen]) {
            @throw [NSException exceptionWithName:@"DshSshForward" reason:@"SSH_PORT_IN_USE：本地转发端口分配失败" userInfo:nil];
        }
        [self publish:@{
            @"phase": @"READY",
            @"message": @"SSH 已连接",
            @"localPort": @(_localPort),
            @"generation": @(myGeneration),
        }];
        [self pump:myGeneration];
        [self closeSession];
        if (!_stopped && myGeneration == [self currentGeneration]) {
            [self publish:@{
                @"phase": @"RECONNECTING",
                @"message": @"SSH 连接已断开，正在重连",
                @"localPort": @(_localPort),
                @"generation": @(myGeneration),
            }];
            [self scheduleReconnect];
        }
    } @catch (NSException *exception) {
        NSLog(@"[DshSshTunnel] SSH connection failed: %@", exception.reason);
        if (!_stopped && myGeneration == [self currentGeneration]) {
            [self closeSession];
            [self publish:@{
                @"phase": @"ERROR",
                @"message": [self friendlyMessage:exception.reason ?: @""],
                @"localPort": @(_localPort),
                @"generation": @(myGeneration),
            }];
        }
    } @finally {
        [_lock lock];
        _connecting = NO;
        [_lock unlock];
    }
}

- (NSString *)sha256Fingerprint:(NMSSHSession *)session {
    size_t length = 0;
    int type = 0;
    const char *hostkey = libssh2_session_hostkey(session.session, &length, &type);
    if (!hostkey || length == 0) return @"";
    unsigned char digest[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256(hostkey, (CC_LONG)length, digest);
    NSData *data = [NSData dataWithBytes:digest length:CC_SHA256_DIGEST_LENGTH];
    return [@"SHA256:" stringByAppendingString:[data base64EncodedStringWithOptions:0]];
}

- (BOOL)startListen {
    [self closeListen];
    _listenFd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (_listenFd < 0) return NO;
    int reuse = 1;
    setsockopt(_listenFd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
    int flags = fcntl(_listenFd, F_GETFL, 0);
    fcntl(_listenFd, F_SETFL, flags | O_NONBLOCK);
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_len = sizeof(addr);
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    addr.sin_port = 0;
    if (bind(_listenFd, (struct sockaddr *)&addr, sizeof(addr)) != 0 || listen(_listenFd, 32) != 0) {
        close(_listenFd);
        _listenFd = -1;
        return NO;
    }
    socklen_t len = sizeof(addr);
    if (getsockname(_listenFd, (struct sockaddr *)&addr, &len) != 0) {
        close(_listenFd);
        _listenFd = -1;
        return NO;
    }
    _localPort = ntohs(addr.sin_port);
    return YES;
}

- (void)pump:(int64_t)myGeneration {
    _pumpThread = [NSThread currentThread];
    libssh2_session_set_blocking(_session.session, 0);
    NSTimeInterval lastKeepAlive = [NSDate timeIntervalSinceReferenceDate];
    char buffer[16384];
    while (!_stopped && myGeneration == [self currentGeneration] && _session.isConnected) {
        fd_set rfds;
        FD_ZERO(&rfds);
        int maxfd = _listenFd;
        if (_listenFd >= 0) FD_SET(_listenFd, &rfds);
        int sshfd = -1;
        if (_session.socket) {
            sshfd = (int)CFSocketGetNative(_session.socket);
            if (sshfd >= 0) {
                FD_SET(sshfd, &rfds);
                if (sshfd > maxfd) maxfd = sshfd;
            }
        }
        for (DshSshConn *conn in _conns) {
            if (conn.fd >= 0) {
                FD_SET(conn.fd, &rfds);
                if (conn.fd > maxfd) maxfd = conn.fd;
            }
        }
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 200000;
        select(maxfd + 1, &rfds, NULL, NULL, &tv);
        if (_listenFd >= 0 && FD_ISSET(_listenFd, &rfds)) {
            [self acceptOne];
        }
        NSMutableArray<DshSshConn *> *dead = [NSMutableArray array];
        for (DshSshConn *conn in _conns) {
            if (conn.fd >= 0 && FD_ISSET(conn.fd, &rfds)) {
                ssize_t n = recv(conn.fd, buffer, sizeof(buffer), 0);
                if (n <= 0) {
                    [dead addObject:conn];
                    continue;
                }
                ssize_t offset = 0;
                while (offset < n) {
                    ssize_t written = libssh2_channel_write(conn.channel, buffer + offset, (size_t)(n - offset));
                    if (written == LIBSSH2_ERROR_EAGAIN) {
                        [NSThread sleepForTimeInterval:0.01];
                        continue;
                    }
                    if (written < 0) {
                        [dead addObject:conn];
                        break;
                    }
                    offset += written;
                }
            }
            ssize_t readCount = libssh2_channel_read(conn.channel, buffer, sizeof(buffer));
            if (readCount > 0) {
                ssize_t offset = 0;
                while (offset < readCount) {
                    ssize_t sent = send(conn.fd, buffer + offset, (size_t)(readCount - offset), 0);
                    if (sent <= 0) {
                        [dead addObject:conn];
                        break;
                    }
                    offset += sent;
                }
            } else if (readCount < 0 && readCount != LIBSSH2_ERROR_EAGAIN) {
                [dead addObject:conn];
            }
            if (libssh2_channel_eof(conn.channel)) {
                [dead addObject:conn];
            }
        }
        for (DshSshConn *conn in dead) {
            [self closeConn:conn];
            [_conns removeObject:conn];
        }
        NSTimeInterval now = [NSDate timeIntervalSinceReferenceDate];
        if (now - lastKeepAlive >= kKeepAliveSeconds) {
            int secondsToNext = 0;
            libssh2_keepalive_send(_session.session, &secondsToNext);
            lastKeepAlive = now;
        }
    }
}

- (void)acceptOne {
    struct sockaddr_in addr;
    socklen_t len = sizeof(addr);
    int client = accept(_listenFd, (struct sockaddr *)&addr, &len);
    if (client < 0) return;
    int nodelay = 1;
    setsockopt(client, IPPROTO_TCP, TCP_NODELAY, &nodelay, sizeof(nodelay));
    int flags = fcntl(client, F_GETFL, 0);
    fcntl(client, F_SETFL, flags | O_NONBLOCK);
    LIBSSH2_CHANNEL *channel = NULL;
    for (int i = 0; i < 50 && !_stopped; i++) {
        channel = libssh2_channel_direct_tcpip_ex(_session.session, "127.0.0.1", (int)_remoteDshPort, "127.0.0.1", _localPort);
        if (channel) break;
        if (libssh2_session_last_errno(_session.session) != LIBSSH2_ERROR_EAGAIN) break;
        [NSThread sleepForTimeInterval:0.05];
    }
    if (!channel) {
        NSLog(@"[DshSshTunnel] direct-tcpip failed");
        close(client);
        return;
    }
    DshSshConn *conn = [[DshSshConn alloc] init];
    conn.fd = client;
    conn.channel = channel;
    [_conns addObject:conn];
}

- (void)closeConn:(DshSshConn *)conn {
    if (conn.channel) {
        libssh2_channel_close(conn.channel);
        libssh2_channel_free(conn.channel);
        conn.channel = NULL;
    }
    if (conn.fd >= 0) {
        close(conn.fd);
        conn.fd = -1;
    }
}

- (void)scheduleReconnect {
    if (_stopped || _reconnectThread.executing) return;
    _reconnectThread = [[NSThread alloc] initWithBlock:^{
        NSArray<NSNumber *> *delays = @[ @1, @2, @4, @8, @16, @30 ];
        for (NSNumber *delay in delays) {
            if (self->_stopped || self->_session.isConnected) return;
            [NSThread sleepForTimeInterval:delay.doubleValue];
            [self connectInternal];
            if (self->_session.isConnected) return;
        }
    }];
    _reconnectThread.name = @"dsh-ssh-reconnect";
    [_reconnectThread start];
}

- (void)closeListen {
    if (_listenFd >= 0) {
        close(_listenFd);
        _listenFd = -1;
    }
}

- (void)closeSession {
    for (DshSshConn *conn in _conns) {
        [self closeConn:conn];
    }
    [_conns removeAllObjects];
    [self closeListen];
    [_session disconnect];
    _session = nil;
}

- (int64_t)currentGeneration {
    [_lock lock];
    int64_t value = _generation;
    [_lock unlock];
    return value;
}

- (NSString *)friendlyMessage:(NSString *)text {
    NSString *lower = text.lowercaseString;
    if ([text containsString:@"SSH_PORT_IN_USE"]) return text;
    if ([lower containsString:@"auth"]) return @"SSH 身份验证失败，请检查用户名和私钥";
    if ([lower containsString:@"password"] || [lower containsString:@"passphrase"]) return @"SSH 私钥口令错误";
    if ([lower containsString:@"connect"] || [lower containsString:@"refused"]) {
        return [NSString stringWithFormat:@"无法连接 SSH 主机：%@", text];
    }
    return text.length > 0 ? text : @"SSH 连接失败";
}

- (void)publish:(NSDictionary *)state {
    void (^callback)(NSDictionary *) = self.onState;
    if (!callback) return;
    dispatch_async(dispatch_get_main_queue(), ^{ callback(state); });
}

@end
