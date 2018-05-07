
## Overview
This is the source code to the [Synced] online virtual world platform.
The Whirled code (forked to make Synced) is released under the BSD License. See the [LICENSE] file for details.

[ActionScript]: http://www.adobe.com/devnet/actionscript.html
[Ant]: http://ant.apache.org/
[Flex]: http://www.adobe.com/devnet/flex.html
[GWT]: http://www.gwtproject.org/
[JDBC]: http://docs.oracle.com/javase/7/docs/technotes/guides/jdbc/
[Java]: http://docs.oracle.com/javase/7/docs/
[LICENSE]: https://github.com/greyhavens/msoy/blob/master/LICENSE
[MySQL]: https://www.mysql.com/
[Postgres]: http://www.postgresql.org/
[Whirled]: http://whirled.com/
[servlets]: http://www.oracle.com/technetwork/java/index-jsp-135475.html

## In-Development
[Errors]:
- Parlor Games

[Implement]:
- Admin Approval System for items that are uploaded to synced.

[Distant Future]:
- HTML5 Creations

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
## Synced Update Commands
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
# Whirled Documentation
Shadowsych and Cactus created a document that outlines how to create a Whirled server, read it here: https://docs.google.com/document/d/1Nb-FskkLhR7-32829tduDgb0a_Fo9ljx6qJB0XqXElc/edit?usp=sharing
