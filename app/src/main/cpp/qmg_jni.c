#include <jni.h>
#include "qmgcore.h"

JNIEXPORT jint JNICALL
Java_com_namal_qmgstudio_Native_nativeInit(JNIEnv *env, jobject thiz, jstring lib, jint w, jint h, jint tol)
{
    const char *p = (*env)->GetStringUTFChars(env, lib, NULL);
    int rc = qmg_init(p, w, h, tol, -1);
    (*env)->ReleaseStringUTFChars(env, lib, p);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_namal_qmgstudio_Native_nativeAddFrame(JNIEnv *env, jobject thiz, jshortArray px)
{
    jshort *p = (*env)->GetShortArrayElements(env, px, NULL);
    if (!p) return -1;
    int n = qmg_add_frame((const uint16_t *)p);
    (*env)->ReleaseShortArrayElements(env, px, p, JNI_ABORT);
    return n;
}

JNIEXPORT jint JNICALL
Java_com_namal_qmgstudio_Native_nativeAddFrameBudget(JNIEnv *env, jobject thiz, jshortArray px, jint budget, jint maxTol)
{
    jshort *p = (*env)->GetShortArrayElements(env, px, NULL);
    if (!p) return -1;
    int n = qmg_add_frame_budget((const uint16_t *)p, budget, maxTol);
    (*env)->ReleaseShortArrayElements(env, px, p, JNI_ABORT);
    return n;
}

JNIEXPORT jint JNICALL
Java_com_namal_qmgstudio_Native_nativeLastTol(JNIEnv *env, jobject thiz)
{
    return qmg_last_tol();
}

JNIEXPORT jlong JNICALL
Java_com_namal_qmgstudio_Native_nativeFinish(JNIEnv *env, jobject thiz, jstring out)
{
    const char *p = (*env)->GetStringUTFChars(env, out, NULL);
    long n = qmg_finish(p);
    (*env)->ReleaseStringUTFChars(env, out, p);
    return (jlong)n;
}

JNIEXPORT void JNICALL
Java_com_namal_qmgstudio_Native_nativeFree(JNIEnv *env, jobject thiz)
{
    qmg_free();
}

JNIEXPORT void JNICALL
Java_com_namal_qmgstudio_Native_nativeConvert(JNIEnv *env, jobject thiz, jintArray argb, jshortArray out,
                                              jint w, jint h, jboolean dither, jint gain)
{
    jint *a = (*env)->GetIntArrayElements(env, argb, NULL);
    jshort *o = (*env)->GetShortArrayElements(env, out, NULL);
    if (a && o) qmg_convert((const int32_t *)a, (uint16_t *)o, w, h, dither ? 1 : 0, gain);
    if (o) (*env)->ReleaseShortArrayElements(env, out, o, 0);
    if (a) (*env)->ReleaseIntArrayElements(env, argb, a, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_namal_qmgstudio_Native_nativeDecOpen(JNIEnv *env, jobject thiz, jint slot, jstring path, jstring lib)
{
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    const char *l = (*env)->GetStringUTFChars(env, lib, NULL);
    int rc = qmg_dec_open(slot, p, l);
    (*env)->ReleaseStringUTFChars(env, path, p);
    (*env)->ReleaseStringUTFChars(env, lib, l);
    return rc;
}

JNIEXPORT jintArray JNICALL
Java_com_namal_qmgstudio_Native_nativeDecInfo(JNIEnv *env, jobject thiz, jint slot)
{
    int v[3] = { 0, 0, 0 };
    qmg_dec_info(slot, &v[0], &v[1], &v[2]);
    jintArray r = (*env)->NewIntArray(env, 3);
    (*env)->SetIntArrayRegion(env, r, 0, 3, v);
    return r;
}

JNIEXPORT jint JNICALL
Java_com_namal_qmgstudio_Native_nativeDecNext(JNIEnv *env, jobject thiz, jint slot, jshortArray out)
{
    jshort *o = (*env)->GetShortArrayElements(env, out, NULL);
    if (!o) return -1;
    int rc = qmg_dec_next(slot, (uint16_t *)o);
    (*env)->ReleaseShortArrayElements(env, out, o, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_com_namal_qmgstudio_Native_nativeDecClose(JNIEnv *env, jobject thiz, jint slot)
{
    qmg_dec_close(slot);
}

JNIEXPORT jintArray JNICALL
Java_com_namal_qmgstudio_Native_nativeVerify(JNIEnv *env, jobject thiz, jstring sysLib, jstring qmg, jstring ownLib)
{
    const char *s = (*env)->GetStringUTFChars(env, sysLib, NULL);
    const char *q = (*env)->GetStringUTFChars(env, qmg, NULL);
    const char *o = (*env)->GetStringUTFChars(env, ownLib, NULL);
    int res[3] = { 0, 0, 0 };
    qmg_sys_verify(s, q, o, res);
    (*env)->ReleaseStringUTFChars(env, sysLib, s);
    (*env)->ReleaseStringUTFChars(env, qmg, q);
    (*env)->ReleaseStringUTFChars(env, ownLib, o);
    jintArray r = (*env)->NewIntArray(env, 3);
    (*env)->SetIntArrayRegion(env, r, 0, 3, res);
    return r;
}
