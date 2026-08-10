cmake_minimum_required(VERSION 3.25)

project(epsilon_smtc LANGUAGES CXX)

if (NOT WIN32)
    message(FATAL_ERROR "epsilon_smtc is only available on Windows")
endif()

find_package(JNI REQUIRED)

add_library(epsilon_smtc SHARED
    src/smtc_core.cpp
    src/smtc_jni.cpp
)

target_compile_features(epsilon_smtc PRIVATE cxx_std_20)
target_include_directories(epsilon_smtc PRIVATE ${JNI_INCLUDE_DIRS} src)
target_link_libraries(epsilon_smtc PRIVATE windowsapp)

add_executable(epsilon_smtc_probe
    src/smtc_core.cpp
    src/smtc_probe.cpp
)

target_compile_features(epsilon_smtc_probe PRIVATE cxx_std_20)
target_include_directories(epsilon_smtc_probe PRIVATE src)
target_link_libraries(epsilon_smtc_probe PRIVATE windowsapp)

foreach(target epsilon_smtc epsilon_smtc_probe)
    target_compile_definitions(${target} PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
    set_property(TARGET ${target} PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    if (MSVC)
        target_compile_options(${target} PRIVATE /W4 /EHsc /permissive- /utf-8)
    endif()
endforeach()

set(SMTC_RESOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../common/src/main/resources/natives/windows-x86_64")
set_target_properties(epsilon_smtc PROPERTIES
    OUTPUT_NAME "epsilon_smtc"
    RUNTIME_OUTPUT_DIRECTORY "${SMTC_RESOURCE_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_DEBUG "${SMTC_RESOURCE_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${SMTC_RESOURCE_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_RELWITHDEBINFO "${SMTC_RESOURCE_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_MINSIZEREL "${SMTC_RESOURCE_DIR}"
)
