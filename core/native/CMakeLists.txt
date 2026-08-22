cmake_minimum_required(VERSION 3.22)
project(jingdu_core_native LANGUAGES CXX)

add_library(jingdu_core_native STATIC
    src/core_api.cpp
)

target_include_directories(jingdu_core_native PUBLIC include)
target_compile_features(jingdu_core_native PUBLIC cxx_std_17)
set_target_properties(jingdu_core_native PROPERTIES CXX_EXTENSIONS OFF)

if (MSVC)
    target_compile_options(jingdu_core_native PRIVATE /W4 /WX)
else()
    target_compile_options(jingdu_core_native PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()

add_executable(jingdu_core_native_smoke tests/core_api_smoke.cpp)
target_link_libraries(jingdu_core_native_smoke PRIVATE jingdu_core_native)
enable_testing()
add_test(NAME jingdu_core_native_smoke COMMAND jingdu_core_native_smoke)
