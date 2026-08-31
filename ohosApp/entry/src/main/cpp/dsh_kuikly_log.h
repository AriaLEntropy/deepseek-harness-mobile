#ifndef DSH_KUIKLY_LOG_H
#define DSH_KUIKLY_LOG_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*KRLogAdapter)(int logLevel, const char *tag, const char *message);
void KRRegisterLogAdapter(KRLogAdapter adapter);

#ifdef __cplusplus
}
#endif

#endif
