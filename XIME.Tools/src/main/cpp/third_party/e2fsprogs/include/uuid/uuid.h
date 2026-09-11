#ifndef _UUID_UUID_H
#define _UUID_UUID_H
#include <sys/types.h>
#include <sys/time.h>
#include <time.h>
typedef unsigned char uuid_t[16];
#define UUID_VARIANT_NCS 	0
#define UUID_VARIANT_DCE 	1
#define UUID_VARIANT_MICROSOFT	2
#define UUID_VARIANT_OTHER	3
#define UUID_TYPE_DCE_TIME   1
#define UUID_TYPE_DCE_RANDOM 4
#ifdef __cplusplus
extern "C" {
#endif
void uuid_clear(uuid_t uu);
int uuid_compare(const uuid_t uu1, const uuid_t uu2);
void uuid_copy(uuid_t dst, const uuid_t src);
void uuid_generate(uuid_t out);
void uuid_generate_random(uuid_t out);
void uuid_generate_time(uuid_t out);
int uuid_is_null(const uuid_t uu);
int uuid_parse(const char *in, uuid_t uu);
void uuid_unparse(const uuid_t uu, char *out);
void uuid_unparse_lower(const uuid_t uu, char *out);
void uuid_unparse_upper(const uuid_t uu, char *out);
time_t uuid_time(const uuid_t uu, struct timeval *ret_tv);
int uuid_type(const uuid_t uu);
int uuid_variant(const uuid_t uu);
#ifdef __cplusplus
}
#endif
#endif
