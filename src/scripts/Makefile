# Makefile to compile the vary3cmd program
ifeq ($(OS),Windows_NT)
    # Windows native or MinGW/MSYS
    DETECTED_OS := Windows
else
    # Unix-like environments (Linux, macOS)
    DETECTED_OS := $(shell uname -s)
endif

BUILD_DIR := build
BACKEND_LIB_DIR := .

ifeq ($(DETECTED_OS),Windows)
	CXX = g++
	CXXFLAGS = -std=c++17 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES
	INCLUDES = -Iheaders -I../backend/headers -I/msys64/ucrt64/include
	LDFLAGS = -L$(BUILD_DIR) -lbackend
	LIBS = -lmpfr -lgmp -lws2_32 -lshlwapi
	SRC = cpp/vary3cmd.cpp cpp/vary.cpp
	OBJ = $(addprefix $(BUILD_DIR)/,$(notdir $(SRC:.cpp=.o)))
	TARGET = $(BUILD_DIR)/vary3cmd.exe
	BACKEND_RUNTIME_LIB = backend.dll
endif
ifeq ($(DETECTED_OS),Linux)
	CXX = g++
	CXXFLAGS = -std=c++17 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES
	INCLUDES = -Iheaders -I../backend/headers 
	LDFLAGS = -L$(BUILD_DIR) -l:libbackend.so -Wl,-rpath,'$$ORIGIN'
	LIBS = -lmpfr -lgmp -lboost_system
	SRC = cpp/vary3cmd.cpp cpp/vary.cpp
	OBJ = $(addprefix $(BUILD_DIR)/,$(notdir $(SRC:.cpp=.o)))
	TARGET = $(BUILD_DIR)/vary3cmd
	BACKEND_RUNTIME_LIB = libbackend.so
endif

all: $(TARGET)

# Final link step. Depends on object files AND the runtime lib being
# staged in build/, so -L$(BUILD_DIR) / rpath '$ORIGIN' resolve correctly.
$(TARGET): $(OBJ) $(BUILD_DIR)/$(BACKEND_RUNTIME_LIB)
	$(CXX) $(CXXFLAGS) $(OBJ) $(LDFLAGS) $(LIBS) -o $@

$(BUILD_DIR)/%.o: cpp/%.cpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

# Stage the shared lib/DLL alongside the executable.
# Linux: needed at runtime since rpath points at $ORIGIN (build/).
# Windows: needed at runtime since Windows searches the exe's own dir;
#          linking itself uses the import library (-lbackend) instead.
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

clean:
ifeq ($(DETECTED_OS),Windows)
	rmdir /S /Q $(BUILD_DIR)
else
	rm -rf $(BUILD_DIR)
endif
.PHONY: all clean
