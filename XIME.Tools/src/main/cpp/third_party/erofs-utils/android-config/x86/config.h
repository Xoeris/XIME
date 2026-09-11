/* config.h.  Generated from config.h.in by configure.  */
/* config.h.in.  Generated from configure.ac by autoheader.  */

/* Define to 1 if fanotify backend is enabled */
/* #undef EROFS_FANOTIFY_ENABLED */

/* The maximum block size which erofs-utils supports */
#define EROFS_MAX_BLOCK_SIZE 4096

/* Enable multi-threading support */
/* #undef EROFS_MT_ENABLED */

/* used FUSE API version */
/* #undef FUSE_USE_VERSION */

/* Define to 1 if `TIOCGWINSZ' requires <sys/ioctl.h>. */
/* #undef GWINSZ_IN_SYS_IOCTL */

/* Define to 1 if you have the `backtrace' function. */
/* #undef HAVE_BACKTRACE */

/* Define to 1 if you have the `copy_file_range' function. */
/* #undef HAVE_COPY_FILE_RANGE */

/* Define to 1 if you have the <curl/curl.h> header file. */
/* #undef HAVE_CURL_CURL_H */

/* Define to 1 if you have the <dirent.h> header file. */
#define HAVE_DIRENT_H 1

/* Define to 1 if you have the <dlfcn.h> header file. */
#define HAVE_DLFCN_H 1

/* Define to 1 if you have the <endian.h> header file. */
#define HAVE_ENDIAN_H 1

/* Define to 1 if you have the <execinfo.h> header file. */
#define HAVE_EXECINFO_H 1

/* Define to 1 if you have the `fallocate' function. */
#define HAVE_FALLOCATE 1

/* Define to 1 if you have the <fcntl.h> header file. */
#define HAVE_FCNTL_H 1

/* Define to 1 if you have the `fstatfs' function. */
#define HAVE_FSTATFS 1

/* Define to 1 if you have the <getopt.h> header file. */
#define HAVE_GETOPT_H 1

/* Define to 1 if you have the `getrandom' function. */
/* #undef HAVE_GETRANDOM */

/* Define to 1 if you have the `getrlimit' function. */
#define HAVE_GETRLIMIT 1

/* Define to 1 if you have the `gettid' function. */
#define HAVE_GETTID 1

/* Define to 1 if you have the `gettimeofday' function. */
#define HAVE_GETTIMEOFDAY 1

/* Define to 1 if you have the <inttypes.h> header file. */
#define HAVE_INTTYPES_H 1

/* Define to 1 if you have the <json-c/json.h> header file. */
/* #undef HAVE_JSON_C_JSON_H */

/* Define to 1 if you have the `lgetxattr' function. */
#define HAVE_LGETXATTR 1

/* Define to 1 if libcurl is found */
/* #undef HAVE_LIBCURL */

/* Define to 1 if libdeflate is found */
/* #undef HAVE_LIBDEFLATE */

/* Define to 1 if you have the `fuse' library (-lfuse). */
/* #undef HAVE_LIBFUSE */

/* Define to 1 if you have the `fuse3' library (-lfuse3). */
/* #undef HAVE_LIBFUSE3 */

/* Define to 1 if liblzma is enabled. */
/* #undef HAVE_LIBLZMA */

/* Define to 1 if you have the `nl-genl-3' library (-lnl-genl-3). */
/* #undef HAVE_LIBNL_GENL_3 */

/* Define to 1 if you have the `pthread' library (-lpthread). */
/* #undef HAVE_LIBPTHREAD */

/* Define to 1 if you have the `qpl' library (-lqpl). */
/* #undef HAVE_LIBQPL */

/* Define to 1 if libselinux is found */
/* #undef HAVE_LIBSELINUX */

/* Define to 1 if you have the `ssl' library (-lssl). */
/* #undef HAVE_LIBSSL */

/* Define to 1 if liburing is found */
/* #undef HAVE_LIBURING */

/* Define to 1 if you have the <liburing.h> header file. */
/* #undef HAVE_LIBURING_H */

/* Define to 1 if libuuid is found */
/* #undef HAVE_LIBUUID */

/* Define to 1 if libxml2 is found */
/* #undef HAVE_LIBXML2 */

/* Define to 1 if you have the <libxml/parser.h> header file. */
/* #undef HAVE_LIBXML_PARSER_H */

/* Define to 1 if you have the `xxhash' library (-lxxhash). */
/* #undef HAVE_LIBXXHASH */

/* Define to 1 if you have the `z' library (-lz). */
/* #undef HAVE_LIBZ */

/* Define to 1 if libzstd is found */
/* #undef HAVE_LIBZSTD */

/* Define to 1 if you have the <limits.h> header file. */
#define HAVE_LIMITS_H 1

/* Define to 1 if you have the <linux/aufs_type.h> header file. */
/* #undef HAVE_LINUX_AUFS_TYPE_H */

/* Define to 1 if you have the <linux/falloc.h> header file. */
#define HAVE_LINUX_FALLOC_H 1

/* Define to 1 if you have the <linux/fs.h> header file. */
#define HAVE_LINUX_FS_H 1

/* Define to 1 if you have the <linux/loop.h> header file. */
#define HAVE_LINUX_LOOP_H 1

/* Define to 1 if you have the <linux/types.h> header file. */
#define HAVE_LINUX_TYPES_H 1

/* Define to 1 if you have the <linux/xattr.h> header file. */
#define HAVE_LINUX_XATTR_H 1

/* Define to 1 if you have the `llistxattr' function. */
#define HAVE_LLISTXATTR 1

/* Define to 1 if you have the `lsetxattr' function. */
#define HAVE_LSETXATTR 1

/* Define to 1 if you have the <lz4.h> header file. */
/* #undef HAVE_LZ4_H */

/* Define to 1 if you have the <lzma.h> header file. */
/* #undef HAVE_LZMA_H */

/* Define to 1 if you have the <memory.h> header file. */
#define HAVE_MEMORY_H 1

/* Define to 1 if memrchr declared in string.h */
#define HAVE_MEMRCHR 1

/* Define to 1 if you have the `memset' function. */
#define HAVE_MEMSET 1

/* Define to 1 if you have the <netlink/genl/genl.h> header file. */
/* #undef HAVE_NETLINK_GENL_GENL_H */

/* Define to 1 if openssl is found */
/* #undef HAVE_OPENSSL */

/* Define to 1 if you have the <openssl/evp.h> header file. */
/* #undef HAVE_OPENSSL_EVP_H */

/* Define to 1 if you have the <openssl/hmac.h> header file. */
/* #undef HAVE_OPENSSL_HMAC_H */

/* Define to 1 if you have the `posix_fadvise' function. */
#define HAVE_POSIX_FADVISE 1

/* Define to 1 if you have the `pread64' function. */
#define HAVE_PREAD64 1

/* Define to 1 if you have the <pthread.h> header file. */
/* #undef HAVE_PTHREAD_H */

/* Define to 1 if you have the `pwrite64' function. */
#define HAVE_PWRITE64 1

/* Define to 1 if you have the `pwritev' function. */
#define HAVE_PWRITEV 1

/* Define to 1 if qpl is found */
/* #undef HAVE_QPL */

/* Define to 1 if you have the <qpl/qpl.h> header file. */
/* #undef HAVE_QPL_QPL_H */

/* Define to 1 if you have the `realpath' function. */
#define HAVE_REALPATH 1

/* Define to 1 if you have the `sendfile' function. */
#define HAVE_SENDFILE 1

/* Define to 1 if you have the <stddef.h> header file. */
#define HAVE_STDDEF_H 1

/* Define to 1 if you have the <stdint.h> header file. */
#define HAVE_STDINT_H 1

/* Define to 1 if you have the <stdlib.h> header file. */
#define HAVE_STDLIB_H 1

/* Define to 1 if you have the `strdup' function. */
#define HAVE_STRDUP 1

/* Define to 1 if you have the `strerror' function. */
#define HAVE_STRERROR 1

/* Define to 1 if you have the <strings.h> header file. */
#define HAVE_STRINGS_H 1

/* Define to 1 if you have the <string.h> header file. */
#define HAVE_STRING_H 1

/* Define to 1 if you have the `strrchr' function. */
#define HAVE_STRRCHR 1

/* Define to 1 if you have the `strtoull' function. */
#define HAVE_STRTOULL 1

/* Define to 1 if the system has the type `struct fanotify_event_info_range'.
   */
/* #undef HAVE_STRUCT_FANOTIFY_EVENT_INFO_RANGE */

/* Define to 1 if `cache_readdir' is a member of `struct fuse_file_info'. */
/* #undef HAVE_STRUCT_FUSE_FILE_INFO_CACHE_READDIR */

/* Define to 1 if `keep_cache' is a member of `struct fuse_file_info'. */
/* #undef HAVE_STRUCT_FUSE_FILE_INFO_KEEP_CACHE */

/* Define to 1 if `st_atim' is a member of `struct stat'. */
#define HAVE_STRUCT_STAT_ST_ATIM 1

/* Define to 1 if `st_atimensec' is a member of `struct stat'. */
#define HAVE_STRUCT_STAT_ST_ATIMENSEC 1

/* Define to 1 if `st_rdev' is a member of `struct stat'. */
#define HAVE_STRUCT_STAT_ST_RDEV 1

/* Define to 1 if you have the `sysconf' function. */
#define HAVE_SYSCONF 1

/* Define to 1 if you have the <sys/fanotify.h> header file. */
/* #undef HAVE_SYS_FANOTIFY_H */

/* Define to 1 if you have the <sys/ioctl.h> header file. */
#define HAVE_SYS_IOCTL_H 1

/* Define to 1 if you have the <sys/mman.h> header file. */
#define HAVE_SYS_MMAN_H 1

/* Define to 1 if you have the <sys/random.h> header file. */
#define HAVE_SYS_RANDOM_H 1

/* Define to 1 if you have the <sys/resource.h> header file. */
#define HAVE_SYS_RESOURCE_H 1

/* Define to 1 if you have the <sys/sendfile.h> header file. */
#define HAVE_SYS_SENDFILE_H 1

/* Define to 1 if you have the <sys/statfs.h> header file. */
#define HAVE_SYS_STATFS_H 1

/* Define to 1 if you have the <sys/stat.h> header file. */
#define HAVE_SYS_STAT_H 1

/* Define to 1 if you have the <sys/sysmacros.h> header file. */
#define HAVE_SYS_SYSMACROS_H 1

/* Define to 1 if you have the <sys/time.h> header file. */
#define HAVE_SYS_TIME_H 1

/* Define to 1 if you have the <sys/types.h> header file. */
#define HAVE_SYS_TYPES_H 1

/* Define to 1 if you have the <sys/uio.h> header file. */
#define HAVE_SYS_UIO_H 1

/* Define to 1 if you have the <sys/xattr.h> header file. */
#define HAVE_SYS_XATTR_H 1

/* Define to 1 if you have the <unistd.h> header file. */
#define HAVE_UNISTD_H 1

/* Define to 1 if you have the `utimensat' function. */
#define HAVE_UTIMENSAT 1

/* Define to 1 if xxhash is found */
/* #undef HAVE_XXHASH */

/* Define to 1 if you have the <xxhash.h> header file. */
/* #undef HAVE_XXHASH_H */

/* Define to 1 if zlib is found */
/* #undef HAVE_ZLIB */

/* Define to 1 if you have the <zlib.h> header file. */
/* #undef HAVE_ZLIB_H */

/* Define to 1 if you have the `ZSTD_getFrameContentSize' function. */
/* #undef HAVE_ZSTD_GETFRAMECONTENTSIZE */

/* Define to 1 if you have the <zstd.h> header file. */
/* #undef HAVE_ZSTD_H */

/* Define to the sub-directory where libtool stores uninstalled libraries. */
#define LT_OBJDIR ".libs/"

/* Define to 1 if lz4hc is enabled. */
/* #undef LZ4HC_ENABLED */

/* Define to 1 if lz4 is enabled. */
/* #undef LZ4_ENABLED */

/* Define to 1 if OCI registry is enabled */
/* #undef OCIEROFS_ENABLED */

/* Name of package */
#define PACKAGE "erofs-utils"

/* Define to the address where bug reports for this package should be sent. */
#define PACKAGE_BUGREPORT "linux-erofs@lists.ozlabs.org"

/* Define to the full name of this package. */
#define PACKAGE_NAME "erofs-utils"

/* Define to the full name and version of this package. */
#define PACKAGE_STRING "erofs-utils 1.9.3"

/* Define to the one symbol short name of this package. */
#define PACKAGE_TARNAME "erofs-utils"

/* Define to the home page for this package. */
#define PACKAGE_URL ""

/* Define to the version of this package. */
#define PACKAGE_VERSION "1.9.3"

/* Define to 1 if s3 is enabled */
/* #undef S3EROFS_ENABLED */

/* Define to 1 if you have the ANSI C header files. */
#define STDC_HEADERS 1

/* Version number of package */
#define VERSION "1.9.3"

/* Define for Solaris 2.5.1 so the uint64_t typedef from <sys/synch.h>,
   <pthread.h>, or <semaphore.h> is not used. If the typedef were allowed, the
   #define below would cause a syntax error. */
/* #undef _UINT64_T */

/* Define to `__inline__' or `__inline' if that's what the C compiler
   calls it, or to nothing if 'inline' is not supported under any name.  */
#ifndef __cplusplus
/* #undef inline */
#endif

/* Define to the type of a signed integer type of width exactly 64 bits if
   such a type exists and the standard includes do not define it. */
/* #undef int64_t */

/* Define to `unsigned int' if <sys/types.h> does not define. */
/* #undef size_t */

/* Define to `int' if <sys/types.h> does not define. */
/* #undef ssize_t */

/* Define to the type of an unsigned integer type of width exactly 64 bits if
   such a type exists and the standard includes do not define it. */
/* #undef uint64_t */
