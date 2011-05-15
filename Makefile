SHELL := /bin/bash

default: simulation client

simulation: common
	javac -g aoi/GameStateSender.java aoi/PlayerListener.java aoi/Simulation.java aoi/ClusterServerSide.java aoi/ClusterDatabase.java

client: common
	javac -g stdlib/StdDraw.java aoi/Intelligence.java aoi/HigherBeing.java aoi/GUI.java aoi/Client.java aoi/ClientConfiguration.java

common: aoi/PlayerState.java aoi/PlayerControlParameters.java aoi/GameState.java
	javac -g aoi/PlayerState.java aoi/PlayerControlParameters.java aoi/GameState.java aoi/Bullet.java aoi/Cluster.java aoi/Arena.java

doc:
	javadoc -d doc aoi

clean:
	rm -f *~ *.class aoi/*~ aoi/*.class stdlib/*~ stdlib/*.class

run_serv:
	java -ea aoi/Simulation

run_client0:
	java -ea aoi/Client < client0local.conf

run_client1:
	java -ea aoi/Client < client1local.conf
