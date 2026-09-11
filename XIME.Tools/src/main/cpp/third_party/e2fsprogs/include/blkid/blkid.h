#ifndef _BLKID_BLKID_H
#define _BLKID_BLKID_H
#ifdef __cplusplus
extern "C" {
#endif
typedef struct blkid_struct_cache *blkid_cache;
int blkid_get_cache(blkid_cache *cache, const char *filename);
void blkid_put_cache(blkid_cache cache);
char *blkid_get_tag_value(blkid_cache cache, const char *tagname, const char *devname);
char *blkid_get_devname(blkid_cache cache, const char *token, const char *value);
#ifdef __cplusplus
}
#endif
#endif
