
## Documentation
All documentation to setting-up and running this game is provided in this public Google Document:  
https://docs.google.com/document/d/1Nb-FskkLhR7-32829tduDgb0a_Fo9ljx6qJB0XqXElc/edit?usp=sharing  
Have fun :)!  

## Build Commands
```
% ant -p
Buildfile: .../msoy/build.xml

Main targets:

 ant asclient      Builds the Flash world client.
 ant clean         Cleans out compiled code.
 ant compile       Builds java class files.
 ant distall       Builds entire system (does not package).
 ant distcleanall  Fully cleans out the application and all subprojects.
 ant flashapps     Builds all Flash clients and applets.
 ant gclients      Builds all GWT clients (use -Dpages to choose which).
 ant genasync      Regenerates GWT Async interfaces.
 ant package       Builds entire system and generates dpkg files.
 ant tests         Runs unit tests.
 ant thane-client  Builds the thane game client.
 ant viewer        Build the viewer for the SDK.
Default target: compile
```
## Glowbe Build Commands
```
 ant dist          Builds the MSOY .jar Files.
 ant asclient      Builds the Flash world client.
 ant compile       Builds java class files.
 ant flashapps     Builds all Flash clients and applets.
 ant gclients      Builds all GWT clients (use -Dpages to choose which).
 ant tests         Runs unit tests.
 ant thane-client  Builds the thane game client.
 ant viewer        Build the viewer for the SDK.
```
## Server Commands
```
 sudo ./bin/msoyserver  Starts the Game Server
 sudo ./bin/burlserver  Starts the Bureau Server (Launcher Games)
 sudo fuser -n tcp -k 80 Turns off all servers running on port 80
```
