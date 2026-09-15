cmake_minimum_required(VERSION 3.22.1)
project("tagJNI")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(TAGLIB_SRC   "${CMAKE_CURRENT_SOURCE_DIR}/taglib")
set(TAGLIB_BUILD "${CMAKE_CURRENT_BINARY_DIR}/taglib-build")

# Lock cache variables before add_subdirectory: TagLib root CMakeLists reads these options
foreach(o BUILD_SHARED_LIBS BUILD_BINDINGS BUILD_TESTING BUILD_EXAMPLES)
  set(${o} OFF CACHE BOOL "" FORCE)
endforeach()
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
set(WITH_ZLIB   OFF CACHE BOOL "" FORCE)
set(WITH_MP4    ON  CACHE BOOL "" FORCE)
set(WITH_VORBIS ON  CACHE BOOL "" FORCE)

# Apply the Auralis patches to the pinned TagLib checkout.
#
# The submodule stays pinned to the upstream commit and the patches are applied to
# its working tree here, so a fresh clone builds the same code without needing a
# fork. `git` is necessarily available on any machine that could clone the
# submodule in the first place.
#
# Note: this leaves the submodule working tree modified. Restoring it with
# `git -C app/src/main/cpp/taglib checkout -- .` is harmless; the next configure
# applies the patches again.
set(TAGLIB_PATCH_DIR "${CMAKE_CURRENT_SOURCE_DIR}/taglib-patches")
find_package(Git REQUIRED)
file(GLOB TAGLIB_PATCHES "${TAGLIB_PATCH_DIR}/*.patch")
list(SORT TAGLIB_PATCHES)
foreach(patch IN LISTS TAGLIB_PATCHES)
  execute_process(
    COMMAND "${GIT_EXECUTABLE}" apply --reverse --check "${patch}"
    WORKING_DIRECTORY "${TAGLIB_SRC}"
    RESULT_VARIABLE patch_already_applied
    OUTPUT_QUIET ERROR_QUIET)
  if(patch_already_applied EQUAL 0)
    message(STATUS "TagLib patch already applied: ${patch}")
    continue()
  endif()

  execute_process(
    COMMAND "${GIT_EXECUTABLE}" apply --check "${patch}"
    WORKING_DIRECTORY "${TAGLIB_SRC}"
    RESULT_VARIABLE patch_applies
    OUTPUT_QUIET ERROR_VARIABLE patch_error)
  if(NOT patch_applies EQUAL 0)
    message(FATAL_ERROR
      "TagLib patch does not apply: ${patch}\n"
      "The pinned TagLib revision most likely changed, so the patch has to be "
      "re-checked against it.\n${patch_error}")
  endif()

  execute_process(
    COMMAND "${GIT_EXECUTABLE}" apply "${patch}"
    WORKING_DIRECTORY "${TAGLIB_SRC}"
    RESULT_VARIABLE patch_result
    ERROR_VARIABLE patch_error)
  if(NOT patch_result EQUAL 0)
    message(FATAL_ERROR "Failed to apply TagLib patch ${patch}:\n${patch_error}")
  endif()
  message(STATUS "Applied TagLib patch: ${patch}")
endforeach()

add_subdirectory("${TAGLIB_SRC}" "${TAGLIB_BUILD}")

add_library(tagJNI SHARED
        taglib_jni.cpp
        JInputStream.cpp
        JTagMap.cpp
        JMetadataBuilder.cpp
        JClassRef.cpp
        JObjectRef.cpp
        JStringRef.cpp
        JByteArrayRef.cpp)

set(TAGLIB_INCLUDE_DIRS
    "${TAGLIB_SRC}"
    "${TAGLIB_SRC}/taglib"
    "${TAGLIB_SRC}/taglib/toolkit"
    "${TAGLIB_SRC}/taglib/mpeg"
    "${TAGLIB_SRC}/taglib/mpeg/id3v1"
    "${TAGLIB_SRC}/taglib/mpeg/id3v2"
    "${TAGLIB_SRC}/taglib/mpeg/id3v2/frames"
    "${TAGLIB_SRC}/taglib/ogg"
    "${TAGLIB_SRC}/taglib/ogg/vorbis"
    "${TAGLIB_SRC}/taglib/ogg/opus"
    "${TAGLIB_SRC}/taglib/flac"
    "${TAGLIB_SRC}/taglib/mp4"
    "${TAGLIB_SRC}/taglib/riff"
    "${TAGLIB_SRC}/taglib/riff/wav"
    "${TAGLIB_BUILD}"
    "${TAGLIB_BUILD}/taglib"
)

target_include_directories(tagJNI PRIVATE ${TAGLIB_INCLUDE_DIRS})

target_link_options(tagJNI PRIVATE "-Wl,--exclude-libs,ALL,-z,max-page-size=16384")

target_link_libraries(tagJNI PRIVATE android log tag)
