//
// Created by Rae-An Andres on 8/16/25.
//
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "OpenCVNDK", __VA_ARGS__)

using namespace cv;

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