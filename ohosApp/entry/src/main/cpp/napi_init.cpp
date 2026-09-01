#include "napi/native_api.h"
#include "libshared_api.h"
#include "dsh_kuikly_log.h"
#include <atomic>
#include <cstdlib>
#include <hilog/log.h>
#include <string>
#include <vector>

static constexpr unsigned int DSH_LOG_DOMAIN = 0x0D51;
static napi_threadsafe_function g_mainTsfn = nullptr;

static std::string NapiToUtf8(napi_env env, napi_value value) {
    size_t length = 0;
    napi_get_value_string_utf8(env, value, nullptr, 0, &length);
    if (length == 0) {
        return std::string();
    }
    std::vector<char> buf(length + 1);
    napi_get_value_string_utf8(env, value, buf.data(), buf.size(), &length);
    return std::string(buf.data(), length);
}

static void DshKuiklyLogAdapter(int logLevel, const char *tag, const char *message) {
    (void)logLevel;
    OH_LOG_Print(LOG_APP, LOG_INFO, DSH_LOG_DOMAIN, tag != nullptr ? tag : "KLog", "%{public}s",
                 message != nullptr ? message : "");
}

static void CallJsOnMain(napi_env env, napi_value jsCallback, void *context, void *data) {
    (void)context;
    auto *payload = static_cast<std::string *>(data);
    if (env == nullptr || payload == nullptr) {
        delete payload;
        return;
    }
    if (jsCallback != nullptr) {
        napi_value str = nullptr;
        napi_create_string_utf8(env, payload->c_str(), payload->size(), &str);
        napi_value undefined = nullptr;
        napi_get_undefined(env, &undefined);
        napi_value args[1] = {str};
        napi_call_function(env, undefined, jsCallback, 1, args, nullptr);
    }
    delete payload;
}

static napi_value SetMainHandler(napi_env env, napi_callback_info info) {
    napi_value undefined = nullptr;
    napi_get_undefined(env, &undefined);
    if (g_mainTsfn != nullptr) {
        return undefined;
    }
    size_t argc = 1;
    napi_value jsFunc = nullptr;
    napi_get_cb_info(env, info, &argc, &jsFunc, nullptr, nullptr);
    if (argc < 1 || jsFunc == nullptr) {
        return undefined;
    }
    napi_value resourceName = nullptr;
    napi_create_string_utf8(env, "dshPostToMain", NAPI_AUTO_LENGTH, &resourceName);
    napi_status status = napi_create_threadsafe_function(env, jsFunc, nullptr, resourceName, 0, 1, nullptr, nullptr,
                                                         nullptr, CallJsOnMain, &g_mainTsfn);
    if (status != napi_ok) {
        OH_LOG_Print(LOG_APP, LOG_ERROR, DSH_LOG_DOMAIN, "DshOhos", "napi_create_threadsafe_function failed %{public}d",
                     static_cast<int>(status));
        g_mainTsfn = nullptr;
    }
    return undefined;
}

static napi_value PostToMain(napi_env env, napi_callback_info info) {
    napi_value undefined = nullptr;
    napi_get_undefined(env, &undefined);
    if (g_mainTsfn == nullptr) {
        return undefined;
    }
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    if (argc < 1 || argv[0] == nullptr) {
        return undefined;
    }
    auto *payload = new std::string(NapiToUtf8(env, argv[0]));
    napi_acquire_threadsafe_function(g_mainTsfn);
    napi_status posted = napi_call_threadsafe_function(g_mainTsfn, payload, napi_tsfn_nonblocking);
    napi_release_threadsafe_function(g_mainTsfn, napi_tsfn_release);
    if (posted != napi_ok) {
        delete payload;
    }
    return undefined;
}

static napi_value InitKuikly(napi_env env, napi_callback_info info) {
    (void)info;
    static std::atomic<bool> logRegistered{false};
    if (!logRegistered.exchange(true)) {
        KRRegisterLogAdapter(&DshKuiklyLogAdapter);
    }
    auto api = libshared_symbols();
    int handler = api->kotlin.root.initKuikly();
    dsh_http_init();
    napi_value result;
    napi_create_int32(env, handler, &result);
    return result;
}

struct HttpPostWork {
    napi_async_work work = nullptr;
    napi_deferred deferred = nullptr;
    std::string url;
    std::string body;
    std::string token;
    std::string result;
};

static void ExecuteHttpPost(napi_env env, void *data) {
    (void)env;
    auto *work = static_cast<HttpPostWork *>(data);
    void *rawPtr = dsh_http_post_json(const_cast<char *>(work->url.c_str()), const_cast<char *>(work->body.c_str()),
                                      const_cast<char *>(work->token.c_str()));
    char *raw = static_cast<char *>(rawPtr);
    if (raw == nullptr) {
        work->result = "-1\nnetworkkmm returned null\n0\n";
        return;
    }
    work->result = raw;
    free(raw);
}

static void CompleteHttpPost(napi_env env, napi_status status, void *data) {
    auto *work = static_cast<HttpPostWork *>(data);
    napi_value result;
    napi_create_string_utf8(env, work->result.c_str(), NAPI_AUTO_LENGTH, &result);
    if (status == napi_ok) {
        napi_resolve_deferred(env, work->deferred, result);
    } else {
        napi_reject_deferred(env, work->deferred, result);
    }
    napi_delete_async_work(env, work->work);
    delete work;
}

static napi_value DshHttpPostJson(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3] = {nullptr, nullptr, nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    auto *work = new HttpPostWork();
    if (argc > 0 && args[0] != nullptr) {
        work->url = NapiToUtf8(env, args[0]);
    }
    if (argc > 1 && args[1] != nullptr) {
        work->body = NapiToUtf8(env, args[1]);
    }
    if (argc > 2 && args[2] != nullptr) {
        work->token = NapiToUtf8(env, args[2]);
    }
    napi_value promise;
    napi_create_promise(env, &work->deferred, &promise);
    napi_value resourceName;
    napi_create_string_utf8(env, "dshHttpPostJson", NAPI_AUTO_LENGTH, &resourceName);
    napi_create_async_work(env, nullptr, resourceName, ExecuteHttpPost, CompleteHttpPost, work, &work->work);
    napi_queue_async_work(env, work->work);
    return promise;
}

EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor desc[] = {
        {"initKuikly", nullptr, InitKuikly, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"dshHttpPostJson", nullptr, DshHttpPostJson, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setMainHandler", nullptr, SetMainHandler, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"postToMain", nullptr, PostToMain, nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
    return exports;
}
EXTERN_C_END

static napi_module demoModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "entry",
    .nm_priv = ((void *)0),
    .reserved = {0},
};

extern "C" __attribute__((constructor)) void RegisterEntryModule(void) { napi_module_register(&demoModule); }
