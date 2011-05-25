SHELL := /bin/bash

default: simulation client

simulation: common
	javac -g aoi/GameStateSender.java aoi/PlayerListener.java aoi/Simulation.java aoi/ClusterServerSide.java aoi/ClusterDatabase.java aoi/ProtoCluster.java aoi/Server.java

client: common
	javac -g stdlib/StdDraw.java aoi/Intelligence.java aoi/HigherBeing.java aoi/GUI.java aoi/Client.java aoi/ClientConfiguration.java aoi/Nonhuman.java

common:
	javac -g aoi/PlayerState.java aoi/PlayerControlParameters.java aoi/GameState.java aoi/Bullet.java aoi/Cluster.java aoi/Arena.java aoi/SimulationUtils.java

doc:
	javadoc -d doc aoi

clean:
	rm -f *~ *.class aoi/*~ aoi/*.class stdlib/*~ stdlib/*.class

run_serv:
	java -ea aoi/Server < server.conf

run_client0:
	java -ea aoi/Client < client0local.conf

run_client0r:
	java -ea aoi/Client < client0remote.conf

run_client1:
	java -ea aoi/Client < client1local.conf

run_client1r:
	java -ea aoi/Client < client1remote.conf