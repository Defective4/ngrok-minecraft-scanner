# ngrok Minecraft scanner

A scanner utility for Minecraft servers shared over ngrok.  

> **Disclaimer**  
> This software was made for educational purposes **only**.  
> I take absolutely no reponsibility for any harm done to unprotected servers discovered using this tool.  
> Remember to always enable whitelist and/or online mode on your servers when sharing them on the Internet!

# About
ngrok Minecraft scanner is a simple server scanning tool that all of ngrok's IPs and ports in search for Minecraft servers.  
It allows you to scan for servers on all versions, as well as servers with `enable-status` set to false, therefore not visible for Minecraft's regular status check.

# Command arguments
```
Usage: java -jar ngrok-minecraft-scanner-1.0.jar [options...]

A scanner utility for Minecraft servers shared over ngrok.

 -d               Don't resolve ngrok hostnames
 -J               Try detecting servers with disabled listings.
                    Makes the scan take more time.
                    May result in false positives.
 -L               Force ONLY legacy server list ping.
 -h               Display help
 -j               Output data in JSON format.
 -l               Fall back to legacy server list ping, in case the first attempt fails.
                    Makes the scan take more time.
 -y               Assume "yes" to all questions.
 -f=<file>        Target while, where discovered servers will be saved.
                    This option won't suppress standard output
 -r=<region>      ngrok region.
                    EU - Europe,
                    US - United States (default)
 -e               Don't print servers with no online players.
 -t=<threads>     Number of threads to use for scanning.
                    Default: 2
 -v               Be more verbose
 -o=<timeout ms>  How many milliseconds should a connection take before timing out.
                    Default: 1000
```

# Usage

## Run a simple scan
```bash
java -jar ngrok-minecraft-scanner.jar -t=4 -o=2500 -r=eu -f=servers.txt
```
This command will scan all ngrok's addresses using 4 separate threads in the `eu` (Europe) region.  
All discovered servers will be saved to a file named `servers.txt`.

### About threads and timeouts
Higher thread counts can drastically increase scanning speed, but some servers might not get discovered, depending on your network connection.  
Try different thread values, and see what works the best in your case.  
Thread count of 4 to 16 is a safe value.  

Lower timeout times can (usually only slightly) decrease scanning time, but servers with high latency might get ommitted.  
Timeout of 2500ms is a pretty safe value

## Scan for unlisted servers
Some servers might have `enable-status` set to false in their properties. In that case they won't get discovered using normal pinging.  
You can use the `-J` option to try to discover unlisted servers.  
The scanner will then make an attempt to "join" a server (if the server didn't respond to standard ping) using invalid protocol ID. If the server responds with a disconnect packet, it means it's online.  

Example:
```bash
java -jar ngrok-minecraft-scanner.jar -t=4 -o=2500 -r=eu -J
```
Remember that enabling this option will slow down the scanning process!  
Also it's not possible to get any info (player count, description, version) about servers discovered using this method.