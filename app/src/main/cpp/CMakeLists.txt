# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("myapplication")

# Includes headers (adjust path if needed)
include_directories(${CMAKE_SOURCE_DIR}/includes)

include_directories(${CMAKE_SOURCE_DIR}/src/main/cpp/nlohmann)

# Declare ggml-base as an imported prebuilt shared library
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so)

# Declare ggml-cpu as an imported prebuilt shared library
add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so)

# Declare libc++_shared as a shared library and set its location
add_library(c++_shared SHARED IMPORTED)
set_target_properties(c++_shared PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libc++_shared.so)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so)

# Declare llama as an imported prebuilt shared library
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so)

# Build your own JNI wrapper library
add_library(native-lib SHARED native-lib.cpp)
add_definitions(-DANDROID)
# Link everything to native-lib in correct dependency order
target_link_libraries(native-lib
        ggml-base
        ggml-cpu
        ggml
        llama
        c++_shared
        android
        log)
