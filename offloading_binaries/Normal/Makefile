CC = arm-linux-androideabi-gcc
CFLAGS = -pie -fPIE -llog
#STATICLIB = -llog -l/media/Lucifer/android/ndk-profiler/obj/local/armeabi-v7a/libandroid-ndk-profiler.a
TARGET = normal

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -Wall $(CFLAGS) -o $@
	cp $(TARGET) /media/Lucifer/yanzi/projects/offloading/RDMAMobileDemo/app/src/main/assets/
clean:
	-rm -f *.o
	-rm -f $(TARGET)

#$(call import-module,android-ndk-profiler)
