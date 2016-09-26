CC = /media/Lucifer/android/lib/android-18-toolchain/bin/arm-linux-androideabi-gcc
#CC = gcc
CFLAGS = -pie -fPIE
TARGET = UDPServer_mobile

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -Wall $(CFLAGS) -o $@
#	cp UDPServer ~/mobileRDMABeach/
clean:
	-rm -f *.o
	-rm -f $(TARGET)
