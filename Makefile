SHELL := /bin/bash

default: simulation client

simulation: common
	javac -g aoi/GameStateSender.java aoi/PlayerListener.java aoi/Simulation.java

client: common
	javac -g stdlib/StdDraw.java aoi/Intelligence.java aoi/HigherBeing.java aoi/Client.java

common: aoi/PlayerState.java aoi/PlayerControlParameters.java aoi/GameState.java
	javac -g aoi/PlayerState.java aoi/PlayerControlParameters.java aoi/GameState.java aoi/Bullet.java

clean:
	rm -f *~ *.class aoi/*~ aoi/*.class stdlib/*~ stdlib/*.class

run_serv:
	java -ea aoi/Simulation

run_client0:
	echo -e "0\ntrue" | java -ea aoi/Client

run_client1:
	echo -e "1\nfalse" | java -ea aoi/Client
