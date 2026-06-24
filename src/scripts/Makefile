# Makefile to compile the vary3cmd program
ifeq ($(OS),Windows_NT)
    # Windows native or MinGW/MSYS
    DETECTED_OS := Windows
else
    # Unix-like environments (Linux, macOS)
    DETECTED_OS := $(shell uname -s)
endif

ifeq ($(DETECTED_OS),Windows)
	CXX = g++
	CXXFLAGS = -std=c++17 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES
	INCLUDES = -Iheaders -I../backend/headers -I/msys64/ucrt64/include
	LDFLAGS = -L. -l:backend.dll
	LIBS = -lmpfr -lgmp -lws2_32 -lshlwapi
	SRC = cpp/vary3cmd.cpp cpp/vary.cpp
	OBJ = $(SRC:.cpp=.o)
	TARGET = vary3cmd.exe
endif

ifeq ($(DETECTED_OS),Linux)
	CXX = g++
	CXXFLAGS = -std=c++17 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES
	INCLUDES = -Iheaders -I../backend/headers 
	# Assumes 
	LDFLAGS = -L. -l:libbackend.so -Wl -rpath='$$ORIGIN' 
	LIBS = -lmpfr -lgmp -lboost_system
	SRC = cpp/vary3cmd.cpp cpp/vary.cpp
	OBJ = $(SRC:.cpp=.o)
	TARGET = vary3cmd
endif

all: $(TARGET)

$(TARGET): $(OBJ)
	$(CXX) $(CXXFLAGS) $(OBJ) $(LDFLAGS) $(LIBS) -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

clean:
	rm -f $(OBJ) $(TARGET)

.PHONY: all clean
