//
// Created by Rae-An Andres on 8/16/25.
//
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <vector>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "OpenCVNDK", __VA_ARGS__)

using namespace cv;

// Global face cascade
CascadeClassifier* faceCascade = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_MainActivity_convertToGray(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrInput,
        jlong matAddrResult) {

    try {
        Mat &inputMat = *(Mat*)matAddrInput;
        Mat &resultMat = *(Mat*)matAddrResult;

        // Convert to grayscale
        cvtColor(inputMat, resultMat, COLOR_RGBA2GRAY);
        LOGD("Grayscale conversion done");
    } catch (const cv::Exception& e) {
        LOGD("OpenCV exception: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_MainActivity_applyBlur(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrInput,
        jlong matAddrResult) {

    try {
        Mat &inputMat = *(Mat*)matAddrInput;
        Mat &resultMat = *(Mat*)matAddrResult;

        // Apply Gaussian blur
        GaussianBlur(inputMat, resultMat, Size(15, 15), 0);
        LOGD("Blur applied");
    } catch (const cv::Exception& e) {
        LOGD("OpenCV exception: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_MainActivity_detectEdges(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrInput,
        jlong matAddrResult) {

    try {
        Mat &inputMat = *(Mat*)matAddrInput;
        Mat &resultMat = *(Mat*)matAddrResult;

        // Convert to grayscale first
        Mat gray;
        cvtColor(inputMat, gray, COLOR_RGBA2GRAY);

        // Apply Canny edge detection
        Canny(gray, resultMat, 50, 150);
        cvtColor(resultMat, resultMat, COLOR_GRAY2RGBA);  // Back to RGBA
    } catch (const cv::Exception& e) {
        LOGD("Canny error: %s", e.what());
    }
}


extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_CameraActivity_convertToGray(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrInput,
        jlong matAddrResult) {

    try {
        Mat &inputMat = *(Mat*)matAddrInput;
        Mat &resultMat = *(Mat*)matAddrResult;

        // Convert to grayscale
        cvtColor(inputMat, resultMat, COLOR_RGBA2GRAY);
        LOGD("Grayscale conversion done");
    } catch (const cv::Exception& e) {
        LOGD("OpenCV exception: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_CameraActivity_applyBlur(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrInput,
        jlong matAddrResult) {

    try {
        Mat &inputMat = *(Mat*)matAddrInput;
        Mat &resultMat = *(Mat*)matAddrResult;

        // Apply Gaussian blur
        GaussianBlur(inputMat, resultMat, Size(15, 15), 0);
        LOGD("Blur applied");
    } catch (const cv::Exception& e) {
        LOGD("OpenCV exception: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_CameraActivity_detectEdges(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrInput,
        jlong matAddrResult) {

    try {
        Mat &inputMat = *(Mat*)matAddrInput;
        Mat &resultMat = *(Mat*)matAddrResult;

        // Convert to grayscale first
        Mat gray;
        cvtColor(inputMat, gray, COLOR_RGBA2GRAY);

        // Apply Canny edge detection
        Canny(gray, resultMat, 50, 150);
        cvtColor(resultMat, resultMat, COLOR_GRAY2RGBA);  // Back to RGBA
    } catch (const cv::Exception& e) {
        LOGD("Canny error: %s", e.what());
    }
}




// Load the model (call from Java/Kotlin once)
extern "C" JNIEXPORT void JNICALL
Java_com_raeanandres_opencvndkapp_CameraActivity_loadFaceCascade(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    faceCascade = new CascadeClassifier(std::string(path));
    env->ReleaseStringUTFChars(modelPath, path);

    if (faceCascade->empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "ARApp", "Failed to load cascade");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "ARApp", "Cascade loaded successfully");
    }
}

// Detect faces and return rectangles
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_raeanandres_opencvndkapp_CameraActivity_detectFaces(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddr) {

    Mat& frame = *(Mat*)matAddr;
    Mat gray;
    cvtColor(frame, gray, COLOR_RGBA2GRAY);
    equalizeHist(gray, gray);

    std::vector<Rect> faces;
    if (faceCascade) {
        faceCascade->detectMultiScale(gray, faces, 1.1, 3, 0, Size(30, 30));
    }

    // Convert Rects to jlongArray [x, y, width, height, ...]
    jlongArray result = env->NewLongArray(faces.size() * 4);
    if (faces.size() > 0) {
        std::vector<jlong> buffer;
        for (auto& face : faces) {
            buffer.push_back(face.x);
            buffer.push_back(face.y);
            buffer.push_back(face.width);
            buffer.push_back(face.height);
        }
        env->SetLongArrayRegion(result, 0, faces.size() * 4, buffer.data());
    }

    return result;
}