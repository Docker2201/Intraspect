cmake_minimum_required(VERSION 3.20)
project(chekator_occt_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED OCCT_ROOT)
  message(FATAL_ERROR "Set -DOCCT_ROOT= to OCCT package root (contains inc/)")
endif()

if(NOT DEFINED JAVA_HOME)
  message(FATAL_ERROR "Set -DJAVA_HOME= to JDK root (contains include/jni.h)")
endif()

if(NOT EXISTS "${JAVA_HOME}/include/jni.h")
  message(FATAL_ERROR "jni.h not found under JAVA_HOME/include: ${JAVA_HOME}")
endif()

set(OCCT_INC "${OCCT_ROOT}/inc")

if(DEFINED OCCT_BINLIB)
  set(OCCT_LIB "${OCCT_BINLIB}/lib")
  set(OCCT_BIN "${OCCT_BINLIB}/bin")
elseif(EXISTS "${OCCT_ROOT}/lib")
  set(OCCT_LIB "${OCCT_ROOT}/lib")
  set(OCCT_BIN "${OCCT_ROOT}/bin")
elseif(EXISTS "${OCCT_ROOT}/win64/vc14/lib")
  set(OCCT_LIB "${OCCT_ROOT}/win64/vc14/lib")
  set(OCCT_BIN "${OCCT_ROOT}/win64/vc14/bin")
else()
  message(FATAL_ERROR "Cannot find OCCT lib/ under OCCT_ROOT or OCCT_BINLIB")
endif()

add_library(chekator_occt_winpath SHARED src/chekator_occt_winpath.cpp)
set_target_properties(chekator_occt_winpath PROPERTIES OUTPUT_NAME "chekator_occt_winpath")

add_library(chekator_occt SHARED src/chekator_occt_bridge.cpp)

target_include_directories(chekator_occt PRIVATE
  ${OCCT_INC}
  "${JAVA_HOME}/include"
)
if(WIN32)
  target_include_directories(chekator_occt PRIVATE "${JAVA_HOME}/include/win32")
  target_link_directories(chekator_occt PRIVATE "${JAVA_HOME}/lib")
endif()

target_link_directories(chekator_occt PRIVATE ${OCCT_LIB})
target_link_libraries(chekator_occt PRIVATE
  jvm
  TKernel TKMath TKG2d TKG3d TKGeomBase TKBRep TKPrim TKTopAlgo TKGeomAlgo TKMesh TKBO
)

if(WIN32)
  set_target_properties(chekator_occt PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/bin/Release"
  )
endif()
