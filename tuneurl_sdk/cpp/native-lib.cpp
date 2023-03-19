#include <jni.h>
#include <string>
#include <iostream>
#include "Fingerprint.h"
#include "FingerprintManager.h"

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dekidea_tuneurl_service_SoundMatchingService_extractFingerprint(JNIEnv* env, jobject /* this */, jobject byteBuffer, jint waveLength) {

    int16_t* wave = (int16_t*) env->GetDirectBufferAddress(byteBuffer);

    Fingerprint* fingerprint = ExtractFingerprint(wave, waveLength);

    jbyteArray result = (env)->NewByteArray(fingerprint->dataSize);

    (env)->SetByteArrayRegion(result, 0, fingerprint->dataSize, reinterpret_cast<const jbyte *>(fingerprint->data));

    FingerprintFree(fingerprint);

    return result;
}


extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dekidea_tuneurl_service_SoundMatchingService_extractFingerprintFromRawFile(JNIEnv* env, jobject /* this */, jstring filePath) {

    const char * _nativeString = env->GetStringUTFChars(filePath, 0);

    Fingerprint* fingerprint = ExtractFingerprintFromRawFile(_nativeString);

    jbyteArray result = (env)->NewByteArray(fingerprint->dataSize);

    (env)->SetByteArrayRegion(result, 0, fingerprint->dataSize, reinterpret_cast<const jbyte*>(fingerprint->data));

    FingerprintFree(fingerprint);

    return result;
}


extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dekidea_tuneurl_service_TuneURLService_extractFingerprint(JNIEnv* env, jobject /* this */, jobject byteBuffer, jint waveLength) {

    int16_t* wave = (int16_t*) env->GetDirectBufferAddress(byteBuffer);

    Fingerprint* fingerprint = ExtractFingerprint(wave, waveLength);

    jbyteArray result = (env)->NewByteArray(fingerprint->dataSize);

    (env)->SetByteArrayRegion(result, 0, fingerprint->dataSize, reinterpret_cast<const jbyte *>(fingerprint->data));

    FingerprintFree(fingerprint);

    return result;
}


extern "C" JNIEXPORT jfloat JNICALL
Java_com_dekidea_tuneurl_service_TuneURLService_getSimilarity(JNIEnv* env, jobject /* this */, jobject byteBuffer1, jint waveLength1, jobject byteBuffer2, jint waveLength2) {

    int16_t* wave1 = (int16_t*) env->GetDirectBufferAddress(byteBuffer1);

    Fingerprint* fingerprint1 = ExtractFingerprint(wave1, waveLength1);

    int16_t* wave2 = (int16_t*) env->GetDirectBufferAddress(byteBuffer2);

    Fingerprint* fingerprint2 = ExtractFingerprint(wave2, waveLength2);

    FingerprintSimilarity similarity = CompareFingerprints(fingerprint1, fingerprint2);

    FingerprintFree(fingerprint1);

    FingerprintFree(fingerprint2);

    return similarity.similarity;
}

