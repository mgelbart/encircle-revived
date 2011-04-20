SHELL := /bin/bash

default: simulation client

simulation: common
	javac -g aoi/SConGameStateSender.java aoi/SConPlayerListener.java aoi/SConSimulation.java

client: common
	javac -g stdlib/StdDraw.java aoi/SConIntelligence.java aoi/HigherBeing.java aoi/SConClient.java

common: aoi/SConPlayerState.java aoi/SConPlayerControlParameters.java aoi/SConGameState.java
	javac -g aoi/SConPlayerState.java aoi/SConPlayerControlParameters.java aoi/SConGameState.java

clean:
	rm -f *~ *.class aoi/*~ aoi/*.class stdlib/*~ stdlib/*.class

run_serv:
	java -ea aoi/SConSimulation

run_client0:
	echo -e "0\ntrue" | java -ea aoi/SConClient

run_client1:
	echo -e "1\nfalse" | java -ea aoi/SConClient
