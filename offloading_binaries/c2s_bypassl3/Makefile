CC = arm-linux-androideabi-gcc
# CC = gcc
CFLAGS = -pie -fPIE
TARGET = c2s_bypassl3

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -Wall $(CFLAGS) -o $@

clean:
	-rm -f *.o
	-rm -f $(TARGET)
