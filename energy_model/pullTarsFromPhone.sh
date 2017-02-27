#!/bin/bash

loc=/Users/yanzi/GDrive/UCSB/Projects/Offloading_2017/Data/$1;

adb devices

if [ ! -d $loc ]; then
  mkdir -p $loc;
fi

for file in $(adb shell ls /sdcard/SSLogger/*.tar.gz | tr -d '\r'); do
  echo $file;
  adb pull $file $loc;
done

adb shell rm /sdcard/SSLogger/*.tar.gz
