cmake_minimum_required(VERSION 3.14)
project(visual)

set(CMAKE_CXX_STANDARD 17)

set(THIRD_PARTY_DIR ${PROJECT_SOURCE_DIR}/third_party)
set(SRC_DIR ${PROJECT_SOURCE_DIR}/src)
set(INCLUDE_DIR ${PROJECT_SOURCE_DIR}/include)

# Поиск зависимостей

# Определяем архитектуру процессора для правильных путей Homebrew
if (${CMAKE_HOST_SYSTEM_PROCESSOR} STREQUAL "arm64")
    set(HOMEBREW_PREFIX "/opt/homebrew")
else()
    set(HOMEBREW_PREFIX "/usr/local")
endif()

message(STATUS "Homebrew prefix: ${HOMEBREW_PREFIX}")

# SDL2
set(SDL2_INCLUDE_DIR ${HOMEBREW_PREFIX}/include)
set(SDL2_LIBRARY ${HOMEBREW_PREFIX}/lib/libSDL2.dylib)

if (NOT EXISTS ${SDL2_INCLUDE_DIR}/SDL2/SDL.h)
    message(FATAL_ERROR "SDL2 not found at ${SDL2_INCLUDE_DIR}")
endif()

# GLEW
set(GLEW_INCLUDE_DIR ${HOMEBREW_PREFIX}/include)
set(GLEW_LIBRARY ${HOMEBREW_PREFIX}/lib/libGLEW.dylib)

if (NOT EXISTS ${GLEW_INCLUDE_DIR}/GL/glew.h)
    message(FATAL_ERROR "GLEW not found at ${GLEW_INCLUDE_DIR}")
endif()

# OpenGL
find_library(OPENGL_LIBRARY NAMES OpenGL Framework)

# Пути к заголовочным файлам глобально
include_directories(
    ${SDL2_INCLUDE_DIR}
    ${SDL2_INCLUDE_DIR}/SDL2
    ${GLEW_INCLUDE_DIR}
    ${THIRD_PARTY_DIR}/imgui
    ${THIRD_PARTY_DIR}/imgui/backends
    ${THIRD_PARTY_DIR}/implot
    ${INCLUDE_DIR}
)

# Библиотека ImGui
add_library(imgui STATIC
    ${THIRD_PARTY_DIR}/imgui/imgui.cpp
    ${THIRD_PARTY_DIR}/imgui/imgui_draw.cpp
    ${THIRD_PARTY_DIR}/imgui/imgui_demo.cpp
    ${THIRD_PARTY_DIR}/imgui/imgui_tables.cpp
    ${THIRD_PARTY_DIR}/imgui/imgui_widgets.cpp
    ${THIRD_PARTY_DIR}/imgui/backends/imgui_impl_sdl2.cpp
    ${THIRD_PARTY_DIR}/imgui/backends/imgui_impl_opengl3.cpp
)

target_compile_options(imgui PRIVATE -fPIC)

# Библиотека ImPlot
add_library(implot STATIC
    ${THIRD_PARTY_DIR}/implot/implot.cpp
    ${THIRD_PARTY_DIR}/implot/implot_demo.cpp
    ${THIRD_PARTY_DIR}/implot/implot_items.cpp
)

target_link_libraries(implot PRIVATE imgui)
target_compile_options(implot PRIVATE -fPIC)

# Исполняемый файл
add_executable(${PROJECT_NAME} ${SRC_DIR}/main.cpp)

# Линкуем библиотеки
target_link_libraries(${PROJECT_NAME} PRIVATE
    imgui
    implot
    ${SDL2_LIBRARY}
    ${GLEW_LIBRARY}
    ${OPENGL_LIBRARY}
)