# Makefile to compile the vary3cmd program

CXX = g++
CXXFLAGS = -std=c++17 -O2 -Wall -Wextra -Wno-attributes -D_USE_MATH_DEFINES
INCLUDES = -Iheaders -I../backend/headers -I/msys64/ucrt64/include
LDFLAGS = -L. -l:backend.dll -L$(HOME)/code/export/bin
LIBS = -lmpfr -lgmp -lws2_32 -lshlwapi

SRC = cpp/vary3cmd.cpp cpp/vary.cpp
OBJ = $(SRC:.cpp=.o)
TARGET = vary3cmd.exe

all: $(TARGET)

$(TARGET): $(OBJ)
	$(CXX) $(CXXFLAGS) $(OBJ) $(LDFLAGS) $(LIBS) -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

clean:
	rm -f $(OBJ) $(TARGET)

.PHONY: all clean
