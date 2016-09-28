#CC = /media/Lucifer/android/lib/android-18-toolchain/bin/arm-linux-androideabi-gcc
CC1 = arm-linux-androideabi-gcc
CC2 = gcc
CFLAGS = -pie -fPIE
TARGET1 = server_m_send_normaltcp
TARGET2 = server_send_normaltcp

all: $(TARGET1) $(TARGET2)

$(TARGET1): main.c
	$(CC1) main.c -Wall $(CFLAGS) -o $@

$(TARGET2): main.c
	$(CC2) main.c -Wall $(CFLAGS) -o $@

clean:
	-rm -f $(TARGET1)
	-rm -f $(TARGET2)
