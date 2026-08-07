cmake_minimum_required(VERSION 3.22.1)

project("stagemon")

find_package(oboe REQUIRED CONFIG)

add_library(stagemon SHARED
    oboe_engine.cpp
    MetronomeEngine.cpp
)

target_link_libraries(stagemon
    oboe::oboe
    log
    mediandk
    android
)

set_target_properties(stagemon PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)