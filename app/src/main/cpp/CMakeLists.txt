cmake_minimum_required(VERSION 3.22.1)

# Declare the project
project(native-opencv)

# Set OpenCV paths
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../../libs/OpenCV-android-sdk/sdk)
set(OpenCV_INCLUDE_DIRS /Users/rae-anandres/Projects/FE/Mobile/Android/practice/OpenCVNDKApp/app/libs/OpenCV-android-sdk/sdk/native/jni/include)

# Include OpenCV headers
include_directories(${OpenCV_INCLUDE_DIRS})

# Define native library
add_library(
        native-opencv
        SHARED
        native-lib.cpp
)

# Import OpenCV shared library
add_library(opencv_java SHARED IMPORTED)
set_target_properties(opencv_java PROPERTIES
    IMPORTED_LOCATION /Users/rae-anandres/Projects/FE/Mobile/Android/practice/OpenCVNDKApp/app/libs/OpenCV-android-sdk/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so)

# Link libraries
target_link_libraries(
        native-opencv
        opencv_java
        log
)