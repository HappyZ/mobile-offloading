CC = /media/Lucifer/android/lib/android-18-toolchain/bin/arm-linux-androideabi-gcc
#CC = arm-linux-androideabi-gcc
CFLAGS = -pie -fPIE
TARGET = TCPSender_mobile

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
