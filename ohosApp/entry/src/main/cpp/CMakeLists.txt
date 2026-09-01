# the minimum version of CMake.
cmake_minimum_required(VERSION 3.4.1)
project(HarmonyDemo)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

if (DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif ()

if (NOT OHOS_ARCH)
    set(OHOS_ARCH arm64-v8a)
endif ()

set(ENTRY_LIBS_DIR ${NATIVERENDER_ROOT_PATH}/../../../libs/${OHOS_ARCH})
set(KUIKLY_RENDER_SO ${NATIVERENDER_ROOT_PATH}/../../../oh_modules/@kuikly-open/render/libs/${OHOS_ARCH}/libkuikly.so)
set(SHARED_SO ${ENTRY_LIBS_DIR}/libshared.so)
set(PBCURLWRAPPER_SO ${ENTRY_LIBS_DIR}/libpbcurlwrapper.so)
set(OPENSSL_SO ${ENTRY_LIBS_DIR}/libopenssl.so)

include_directories(${NATIVERENDER_ROOT_PATH}
        ${NATIVERENDER_ROOT_PATH}/include)

function(import_prebuilt_shared name so_path)
    if (NOT EXISTS ${so_path})
        message(FATAL_ERROR
                "missing native library ${name}: ${so_path}\n"
                "Run: ./gradlew -c settings.ohos.gradle.kts :shared:downloadPbcurlwrapper")
    endif ()
    add_library(${name} SHARED IMPORTED)
    set_target_properties(${name}
            PROPERTIES
            IMPORTED_LOCATION ${so_path})
endfunction()

import_prebuilt_shared(kuikly_render ${KUIKLY_RENDER_SO})
import_prebuilt_shared(kuikly_shared ${SHARED_SO})
import_prebuilt_shared(pbcurlwrapper ${PBCURLWRAPPER_SO})
import_prebuilt_shared(openssl ${OPENSSL_SO})

add_library(entry SHARED napi_init.cpp)
target_link_directories(entry PUBLIC ${ENTRY_LIBS_DIR})
target_link_libraries(entry PUBLIC
        libace_napi.z.so
        libhilog_ndk.z.so
        kuikly_shared
        kuikly_render
        pbcurlwrapper
        openssl)
