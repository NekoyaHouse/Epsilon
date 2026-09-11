#include "smtc_core.hpp"

#include <jni.h>

namespace {

jstring to_java_string(JNIEnv* env, const std::wstring& value) {
    return env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size()));
}

jbyteArray to_java_bytes(JNIEnv* env, const std::optional<std::vector<std::uint8_t>>& value) {
    if (!value.has_value()) {
        return nullptr;
    }

    const auto& bytes = value.value();
    auto result = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (result != nullptr && !bytes.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()),
                reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return result;
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_me_sofurry_smtc_SmtcNativeBridge_pollNative(JNIEnv* env, jclass) {
    const epsilon::smtc::Snapshot snapshot = epsilon::smtc::poll();
    jclass result_class = env->FindClass("me/sofurry/smtc/SmtcNativeResult");
    if (result_class == nullptr) {
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(result_class, "<init>",
            "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJ[BLjava/lang/String;)V");
    if (constructor == nullptr) {
        return nullptr;
    }

    jstring title = to_java_string(env, snapshot.title);
    jstring artist = to_java_string(env, snapshot.artist);
    jstring album_title = to_java_string(env, snapshot.album_title);
    jstring source_app_id = to_java_string(env, snapshot.source_app_id);
    jbyteArray thumbnail = to_java_bytes(env, snapshot.thumbnail);
    jstring error = to_java_string(env, snapshot.error);

    jobject result = env->NewObject(result_class, constructor,
            static_cast<jboolean>(snapshot.available),
            title,
            artist,
            album_title,
            source_app_id,
            static_cast<jint>(snapshot.playback_status),
            static_cast<jlong>(snapshot.thumbnail_revision),
            thumbnail,
            error);

    env->DeleteLocalRef(title);
    env->DeleteLocalRef(artist);
    env->DeleteLocalRef(album_title);
    env->DeleteLocalRef(source_app_id);
    if (thumbnail != nullptr) {
        env->DeleteLocalRef(thumbnail);
    }
    env->DeleteLocalRef(error);
    env->DeleteLocalRef(result_class);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_me_sofurry_smtc_SmtcNativeBridge_resetNative(JNIEnv*, jclass) {
    epsilon::smtc::reset();
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_8;
}
