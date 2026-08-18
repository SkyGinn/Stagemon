if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "C:/Users/User/.gradle/caches/8.13/transforms/dfeae6252926bbfcabea9a70b959fc8b/transformed/jetified-oboe-1.10.0/prefab/modules/oboe/libs/android.armeabi-v7a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/User/.gradle/caches/8.13/transforms/dfeae6252926bbfcabea9a70b959fc8b/transformed/jetified-oboe-1.10.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

