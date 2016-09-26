CC = gcc
CFLAGS = -Wall
TARGET = UDPSender

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) $(CFLAGS) -o $@

clean:
	-rm -f *.o
	-rm -f $(TARGET)
