/**
 * This is modified based on https://gist.github.com/magnetoz/2329e0912b1386fc063d6ac902289d5e
 * Macbook Pro only supports 802.11n 40MHz mode at max.. so disappointed
 **/

#import <CoreWLAN/CoreWLAN.h>
#import <objc/message.h>

int main(int argc, char* argv[]) {
  @autoreleasepool {
    int ch;
    NSString *ssid = nil, *password = nil;
    NSInteger selected_channel = 44;  // default is channel 9, 2.4GHz
    NSInteger selected_channel_width = 3;  // channel 40MHz is in used

    while((ch = getopt(argc, argv, "s:p:c:w:h")) != -1) {
      switch(ch) {
      case 's':
        ssid = [NSString stringWithUTF8String:optarg];
        break;
      case 'p':
        password = [NSString stringWithUTF8String:optarg];
        break;
      case 'c':
        selected_channel = [[NSString stringWithUTF8String:optarg] integerValue];
        // printf("result: %lu\n", channel);
        break;
      case 'w':
        selected_channel_width = [[NSString stringWithUTF8String:optarg] integerValue];
        break;
      case '?':
      case 'h':
      default:
        printf("USAGE: %s [-s ssid] [-p password] [-h] command\n", argv[0]);
        printf("\nOPTIONS:\n");
        printf("   -s ssid     SSID\n");
        printf("   -p password WEP password\n");
        printf("   -c channel  channel index\n");
        printf("   -w width    channel width (1, 2, 3 for 20, 40, 80MHz)\n");
        printf("   -h          Print help\n");
        printf("\nCOMMAND:\n");
        printf("   status      Print interface mode\n");
        printf("   start       Start Host AP mode\n");
        printf("   stop        Stop Host AP mode\n");
        return 0;
      }
    }

    NSString *command = nil;
    if(argv[optind]) {
      command = [NSString stringWithUTF8String:argv[optind]];
    }
    CWWiFiClient *wfc = CWWiFiClient.sharedWiFiClient;
    CWInterface *iface = wfc.interface;
    

    if(!command || [command isEqualToString:@"status"]) {
      NSString *mode = nil;
      switch(iface.interfaceMode) {
      case kCWInterfaceModeStation:
        mode = @"Station Mode";
        break;
      case kCWInterfaceModeIBSS:
        mode = @"IBSS Mode";
        break;
      case kCWInterfaceModeHostAP:
        mode = @"HostAP Mode";
        break;
      case kCWInterfaceModeNone:
      default:
        mode = @"None";
      }
      printf("%s\n", [mode UTF8String]);
    } else if([command isEqualToString:@"stop"]) {
      // Stop Host AP mode
      if(getuid() != 0) {
        printf("this may need root (trying anyway)...\n");
      }
      objc_msgSend(iface, @selector(stopHostAPMode));
    } else if([command isEqualToString:@"start"]) {
      if(!ssid) {
        printf("error: an ssid must be specified\n");
        return 1;
      }

      // known security types:
      //   0b10: 2: no securiry
      //   0b10000: 16: wep
      // Note: values [-127..127] have been tried, and all but these return errors.
      //   0b10000000: 128: nothing? need to check
      int securityType = 2;
      if(password) {
        if([password length] < 10) {
          printf("error: password too short (must be >= 10 characters)\n");
          return 1;
        }
        securityType = 16;
      }

      NSSet *chans = [iface supportedWLANChannels];
      // printf("chan count: %lu\n", [chans count]);

      NSEnumerator *enumerator = [chans objectEnumerator];
      CWChannel *channel;
      while ((channel = [enumerator nextObject])) {
        printf("channel: %lu; channel band: %lu\n", [channel channelNumber], [channel channelWidth]);
         // change to 
        if ([channel channelNumber] == selected_channel
            && [channel channelWidth] == selected_channel_width)
          break;
      }

      if (channel == nil) {
        printf("channel not found\n");
        return 1;
      }
      // return 0;

      // Start Host AP mode
      NSError *error = nil;
      objc_msgSend(iface,
                   //@selector(startIBSSModeWithSSID:security:channel:password:error:),
                   @selector(startHostAPModeWithSSID:securityType:channel:password:error:),
                   //[@"hunter2" dataUsingEncoding:NSUTF8StringEncoding],
                   [ssid dataUsingEncoding:NSUTF8StringEncoding],
                   securityType,
                   channel,
                   // @"abc123ffff",
                   password,
                   &error);
      if(error) {
        printf("startHostAPModeWithSSID error: %s\n", [error.localizedDescription UTF8String]);
        return 1;
      }
    }

    return 0;
  }
}