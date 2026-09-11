#include <jni.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "LucineAI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/**
 * Lucine Motion-Compensated Frame Interpolator v13.0
 *
 * This replaces the old "global push" mesh warp (which just translated every
 * vertex by a fixed 25%-of-width offset) with real per-block motion estimation
 * between the previous and current captured frames, followed by bilinear
 * sampling of that motion field onto the warp mesh. That means:
 *
 *  - Static scenes / static cameras produce ~zero motion vectors -> no warp.
 *  - Panning scenes produce a roughly uniform vector field -> smooth pan.
 *  - Local motion (a hand moving, a ball flying) is captured locally instead
 *    of dragging the whole frame with it.
 *
 * Pipeline per captured frame pair:
 *   1. Downsample RGB_565 buffers to 8-bit luma for cheap comparison.
 *   2. Diamond-search block matching, block by block, to find each block's
 *      best-match motion vector in the other frame (SAD cost).
 *   3. Low-confidence (flat/textureless) blocks borrow from confident
 *      neighbors so block matching noise doesn't show up as jitter.
 *   4. At draw time, nativeFillFlowMeshFromMotion bilinearly samples this
 *      sparse block-vector field at each mesh vertex and scales it by the
 *      interpolation fraction (alpha) and direction, instead of applying one
 *      fixed vector to the whole frame.
 */

#define MAX_BLOCKS_X 64
#define MAX_BLOCKS_Y 64
#define SEARCH_RANGE 12     // max pixel displacement (in luma-downsample space) searched per block
#define BLOCK_SIZE 16        // luma block size in downsampled pixels

typedef struct {
    float mvx;
    float mvy;
    float confidence; // inverse of best SAD, 0 if block was skipped (too flat / no texture)
} MotionBlock;

static MotionBlock g_field[MAX_BLOCKS_Y][MAX_BLOCKS_X];
static int g_blocksX = 0;
static int g_blocksY = 0;
static int g_fieldW = 0;
static int g_fieldH = 0;

// --- RGB565 -> luma ---
static inline unsigned char rgb565ToLuma(unsigned short px) {
    int r = (px >> 11) & 0x1F;
    int g = (px >> 5) & 0x3F;
    int b = px & 0x1F;
    int r8 = (r * 527 + 23) >> 6;
    int g8 = (g * 259 + 33) >> 6;
    int b8 = (b * 527 + 23) >> 6;
    int y = (r8 * 77 + g8 * 150 + b8 * 29) >> 8;
    if (y < 0) y = 0;
    if (y > 255) y = 255;
    return (unsigned char) y;
}

static void toLuma(const unsigned short *pixels, int w, int h, unsigned char *out) {
    for (int i = 0; i < w * h; i++) {
        out[i] = rgb565ToLuma(pixels[i]);
    }
}

static long sadBlock(const unsigned char *a, const unsigned char *b, int w, int h,
                     int ax, int ay, int bx, int by, int bs) {
    long sum = 0;
    for (int y = 0; y < bs; y++) {
        int ay2 = ay + y, by2 = by + y;
        if (ay2 < 0 || ay2 >= h || by2 < 0 || by2 >= h) { sum += 255L * bs; continue; }
        const unsigned char *rowA = a + ay2 * w;
        const unsigned char *rowB = b + by2 * w;
        for (int x = 0; x < bs; x++) {
            int ax2 = ax + x, bx2 = bx + x;
            if (ax2 < 0 || ax2 >= w || bx2 < 0 || bx2 >= w) { sum += 255; continue; }
            sum += abs((int) rowA[ax2] - (int) rowB[bx2]);
        }
    }
    return sum;
}

// Diamond search block matching: finds the vector in `curr` that best matches
// the block taken from `prev` at (bx, by).
static void diamondSearch(const unsigned char *prev, const unsigned char *curr,
                          int w, int h, int bx, int by, int bs,
                          float *outDx, float *outDy, float *outConfidence) {
    static const int LDSP[9][2] = {{0,0},{-2,0},{2,0},{0,-2},{0,2},{-2,-2},{2,-2},{-2,2},{2,2}};
    static const int SDSP[5][2] = {{0,0},{-1,0},{1,0},{0,-1},{0,1}};

    int cx = 0, cy = 0;
    long bestCost = sadBlock(prev, curr, w, h, bx, by, bx, by, bs);
    long flatThreshold = (long) bs * bs / 8;

    int improved = 1;
    int iterations = 0;
    while (improved && iterations < SEARCH_RANGE) {
        improved = 0;
        int bestStep = -1;
        for (int i = 0; i < 9; i++) {
            int nx = cx + LDSP[i][0];
            int ny = cy + LDSP[i][1];
            if (abs(nx) > SEARCH_RANGE || abs(ny) > SEARCH_RANGE) continue;
            long cost = sadBlock(prev, curr, w, h, bx, by, bx + nx, by + ny, bs);
            if (cost < bestCost) {
                bestCost = cost;
                bestStep = i;
            }
        }
        if (bestStep > 0) {
            cx += LDSP[bestStep][0];
            cy += LDSP[bestStep][1];
            improved = 1;
        }
        iterations++;
    }
    improved = 1;
    while (improved) {
        improved = 0;
        int bestStep = -1;
        for (int i = 0; i < 5; i++) {
            int nx = cx + SDSP[i][0];
            int ny = cy + SDSP[i][1];
            if (abs(nx) > SEARCH_RANGE || abs(ny) > SEARCH_RANGE) continue;
            long cost = sadBlock(prev, curr, w, h, bx, by, bx + nx, by + ny, bs);
            if (cost < bestCost) {
                bestCost = cost;
                bestStep = i;
            }
        }
        if (bestStep > 0) {
            cx += SDSP[bestStep][0];
            cy += SDSP[bestStep][1];
            improved = 1;
        }
    }

    *outDx = (float) cx;
    *outDy = (float) cy;
    long zeroCost = sadBlock(prev, curr, w, h, bx, by, bx, by, bs);
    if (zeroCost < flatThreshold && bestCost > 2) {
        *outConfidence = 0.0f;
    } else {
        *outConfidence = 1.0f / (1.0f + (float) bestCost / (bs * bs));
    }
}

/**
 * Computes a sparse motion field between two downsampled RGB_565 frames and
 * stores it for later mesh sampling. Call this on a background thread for
 * each new captured frame pair -- never on the draw thread.
 * prevBuffer/currBuffer must be direct java.nio.ByteBuffers wrapping RGB_565
 * pixel data of size width*height*2 bytes.
 */
JNIEXPORT void JNICALL
Java_xime_media_lucine_VideoInterpolationLucine_nativeComputeMotionField(
        JNIEnv *env, jobject thiz,
        jobject prevBuffer, jobject currBuffer,
        jint width, jint height) {

    if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return;

    unsigned short *prevPixels = (unsigned short *) (*env)->GetDirectBufferAddress(env, prevBuffer);
    unsigned short *currPixels = (unsigned short *) (*env)->GetDirectBufferAddress(env, currBuffer);
    if (prevPixels == NULL || currPixels == NULL) return;

    unsigned char *prevLuma = (unsigned char *) malloc((size_t) width * height);
    unsigned char *currLuma = (unsigned char *) malloc((size_t) width * height);
    if (!prevLuma || !currLuma) {
        free(prevLuma);
        free(currLuma);
        return;
    }

    toLuma(prevPixels, width, height, prevLuma);
    toLuma(currPixels, width, height, currLuma);

    int blocksX = width / BLOCK_SIZE;
    int blocksY = height / BLOCK_SIZE;
    if (blocksX > MAX_BLOCKS_X) blocksX = MAX_BLOCKS_X;
    if (blocksY > MAX_BLOCKS_Y) blocksY = MAX_BLOCKS_Y;
    if (blocksX < 1) blocksX = 1;
    if (blocksY < 1) blocksY = 1;

    static MotionBlock rawField[MAX_BLOCKS_Y][MAX_BLOCKS_X];

    for (int by = 0; by < blocksY; by++) {
        for (int bx = 0; bx < blocksX; bx++) {
            float dx, dy, conf;
            diamondSearch(prevLuma, currLuma, width, height,
                          bx * BLOCK_SIZE, by * BLOCK_SIZE, BLOCK_SIZE, &dx, &dy, &conf);
            rawField[by][bx].mvx = dx;
            rawField[by][bx].mvy = dy;
            rawField[by][bx].confidence = conf;
        }
    }

    // Low-confidence (flat/textureless) blocks borrow from confident neighbors.
    for (int by = 0; by < blocksY; by++) {
        for (int bx = 0; bx < blocksX; bx++) {
            MotionBlock *cell = &rawField[by][bx];
            if (cell->confidence > 0.15f) {
                g_field[by][bx] = *cell;
                continue;
            }
            float sx = 0, sy = 0, wsum = 0;
            for (int oy = -1; oy <= 1; oy++) {
                for (int ox = -1; ox <= 1; ox++) {
                    int nx = bx + ox, ny = by + oy;
                    if (nx < 0 || nx >= blocksX || ny < 0 || ny >= blocksY) continue;
                    MotionBlock *n = &rawField[ny][nx];
                    float w = n->confidence;
                    sx += n->mvx * w;
                    sy += n->mvy * w;
                    wsum += w;
                }
            }
            if (wsum > 0.0001f) {
                g_field[by][bx].mvx = sx / wsum;
                g_field[by][bx].mvy = sy / wsum;
                g_field[by][bx].confidence = wsum / 9.0f;
            } else {
                g_field[by][bx].mvx = 0.0f;
                g_field[by][bx].mvy = 0.0f;
                g_field[by][bx].confidence = 0.0f;
            }
        }
    }

    g_blocksX = blocksX;
    g_blocksY = blocksY;
    g_fieldW = width;
    g_fieldH = height;

    free(prevLuma);
    free(currLuma);
}

static void sampleMotionAt(float fx, float fy, float *outDx, float *outDy) {
    if (g_blocksX <= 0 || g_blocksY <= 0) { *outDx = 0; *outDy = 0; return; }
    float gx = fx * g_blocksX - 0.5f;
    float gy = fy * g_blocksY - 0.5f;
    int x0 = (int) floorf(gx), y0 = (int) floorf(gy);
    int x1 = x0 + 1, y1 = y0 + 1;
    float tx = gx - x0, ty = gy - y0;

    x0 = x0 < 0 ? 0 : (x0 >= g_blocksX ? g_blocksX - 1 : x0);
    x1 = x1 < 0 ? 0 : (x1 >= g_blocksX ? g_blocksX - 1 : x1);
    y0 = y0 < 0 ? 0 : (y0 >= g_blocksY ? g_blocksY - 1 : y0);
    y1 = y1 < 0 ? 0 : (y1 >= g_blocksY ? g_blocksY - 1 : y1);

    MotionBlock *b00 = &g_field[y0][x0];
    MotionBlock *b10 = &g_field[y0][x1];
    MotionBlock *b01 = &g_field[y1][x0];
    MotionBlock *b11 = &g_field[y1][x1];

    float dx0 = b00->mvx * (1 - tx) + b10->mvx * tx;
    float dx1 = b01->mvx * (1 - tx) + b11->mvx * tx;
    float dy0 = b00->mvy * (1 - tx) + b10->mvy * tx;
    float dy1 = b01->mvy * (1 - tx) + b11->mvy * tx;

    *outDx = dx0 * (1 - ty) + dx1 * ty;
    *outDy = dy0 * (1 - ty) + dy1 * ty;
}

/**
 * Smootherstep ease curve for the alpha ramp between hardware frames.
 * Replaces the old degree-10 polynomial, which could exceed [0,1] before
 * clamping and effectively behaved like a step function near the ends.
 */
JNIEXPORT jfloat JNICALL
Java_xime_media_lucine_VideoInterpolationLucine_nativeInterpolate(JNIEnv *env, jobject thiz, jfloat start, jfloat target, jfloat fraction) {
    float t = fraction;
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    float factor = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f); // smootherstep
    return start + (target - start) * factor;
}

/**
 * Builds the warp mesh from the real motion field computed by
 * nativeComputeMotionField, instead of a fabricated global displacement.
 * direction: +1 warps toward curr, -1 warps toward prev.
 * out_mesh must be sized (mesh_width+1)*(mesh_height+1)*2.
 */
JNIEXPORT void JNICALL
Java_xime_media_lucine_VideoInterpolationLucine_nativeFillFlowMeshFromMotion(
        JNIEnv *env, jobject thiz,
        jint mesh_width, jint mesh_height,
        jfloat width, jfloat height,
        jfloat alpha, jfloat direction,
        jfloatArray out_mesh) {

    jfloat *verts = (*env)->GetFloatArrayElements(env, out_mesh, NULL);
    if (verts == NULL) return;

    float scaleX = g_fieldW > 0 ? width / (float) g_fieldW : 1.0f;
    float scaleY = g_fieldH > 0 ? height / (float) g_fieldH : 1.0f;

    int index = 0;
    for (int y = 0; y <= mesh_height; y++) {
        float fy = (float) y / (float) mesh_height;
        float py = fy * height;
        for (int x = 0; x <= mesh_width; x++) {
            float fx = (float) x / (float) mesh_width;
            float px = fx * width;

            float mvx = 0.0f, mvy = 0.0f;
            sampleMotionAt(fx, fy, &mvx, &mvy);

            float dx = mvx * scaleX * alpha * direction;
            float dy = mvy * scaleY * alpha * direction;

            // Soft edge lock so the mesh doesn't tear away from the frame
            // boundary, while still allowing near-full motion in the interior.
            float edgeX = fminf(fx, 1.0f - fx) * 4.0f; if (edgeX > 1.0f) edgeX = 1.0f;
            float edgeY = fminf(fy, 1.0f - fy) * 4.0f; if (edgeY > 1.0f) edgeY = 1.0f;
            float weight = edgeX * edgeY;

            verts[index++] = px + dx * weight;
            verts[index++] = py + dy * weight;
        }
    }

    (*env)->ReleaseFloatArrayElements(env, out_mesh, verts, 0);
}

/**
 * Legacy entry point kept for source compatibility; delegates to the real
 * motion-compensated path. If no motion field has been computed yet, this
 * degrades to an identity mesh (no warp) rather than the old fake
 * fixed-percentage-of-width push.
 */
JNIEXPORT void JNICALL
Java_xime_media_lucine_VideoInterpolationLucine_nativeFillFlowMesh(
        JNIEnv *env, jobject thiz,
        jint mesh_width, jint mesh_height,
        jfloat width, jfloat height,
        jfloat alpha, jfloat direction,
        jfloatArray out_mesh) {
    Java_xime_media_lucine_VideoInterpolationLucine_nativeFillFlowMeshFromMotion(
            env, thiz, mesh_width, mesh_height, width, height, alpha, direction, out_mesh);
}