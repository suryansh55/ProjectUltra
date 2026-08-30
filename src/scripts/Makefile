# Makefile to compile vary3cmd / autopolycmd programs
ifeq ($(OS),Windows_NT)
    DETECTED_OS := Windows
else
    DETECTED_OS := $(shell uname -s)
endif

BUILD_DIR := build
BACKEND_LIB_DIR := .

# Which program to build: vary3cmd (default) or autopolycmd
# Usage: make            -> builds vary3cmd
#        make PROG=autopolycmd
PROG ?= vary3cmd
VALID_PROGS := vary3cmd vary4cmd varyAutoPolyCmd
ifeq ($(filter $(PROG),$(VALID_PROGS)),)
$(error Unknown PROG '$(PROG)'. Valid options: $(VALID_PROGS))
endif

# Sources shared by every program go here.
COMMON_SRC = cpp/vary.cpp
SRC = cpp/$(PROG).cpp $(COMMON_SRC)
OBJ = $(addprefix $(BUILD_DIR)/,$(notdir $(SRC:.cpp=.o)))

ifeq ($(DETECTED_OS),Windows)
	CXX = g++
	CXXFLAGS = -std=c++23 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES
	INCLUDES = -Iheaders -I../backend/headers -I/msys64/ucrt64/include
	LDFLAGS = -L$(BUILD_DIR) -lbackend
	LIBS = -Llib -lmpfr -lgmp -lws2_32 -lshlwapi -lmpfi-0 -lsqlite3-0
	TARGET = $(BUILD_DIR)/$(PROG).exe
	BACKEND_RUNTIME_LIB = backend.dll
endif
ifeq ($(DETECTED_OS),Linux)
	CXX = g++
	CXXFLAGS = -std=c++23 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES -DCOMPUTE_CANADA
	INCLUDES = -Iheaders -I../backend/headers 
	LDFLAGS = -L$(BUILD_DIR) -l:libbackend.so -Wl,-rpath,'$$ORIGIN'
	LIBS = -lmpfr -lgmp -lboost_system -lmpfi -lsqlite3
	TARGET = $(BUILD_DIR)/$(PROG)
	BACKEND_RUNTIME_LIB = libbackend.so
endif

all: $(TARGET)

$(TARGET): $(OBJ) $(BUILD_DIR)/$(BACKEND_RUNTIME_LIB)
	$(CXX) $(CXXFLAGS) $(OBJ) $(LDFLAGS) $(LIBS) -o $@

$(BUILD_DIR)/%.o: cpp/%.cpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

$(BUILD_DIR)/$(BACKEND_RUNTIME_LIB): ./$(BACKEND_LIB_DIR)/$(BACKEND_RUNTIME_LIB) | ./$(BUILD_DIR)
ifeq ($(DETECTED_OS),Windows)
	copy /Y $(subst /,\,$<) $(subst /,\,$@)
else
	cp $< $@
endif

$(BUILD_DIR):
ifeq ($(DETECTED_OS),Windows)
	mkdir $(BUILD_DIR)
else
	mkdir -p $(BUILD_DIR)
endif

# Convenience target: build both programs in one go
both:
	$(MAKE) PROG=vary3cmd
	$(MAKE) PROG=vary4cmd
	$(MAKE) PROG=varyautopolycmd

clean:
ifeq ($(DETECTED_OS),Windows)
	if exist $(BUILD_DIR) rmdir /S /Q $(BUILD_DIR)
else
	rm -rf $(BUILD_DIR)
endif

.PHONY: all clean both
