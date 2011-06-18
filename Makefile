SHELL := /bin/bash

classpath := .:lib/commons-codec-1.5.jar

compileopts := -g -classpath $(classpath)

runopts := -classpath $(classpath) -Xmx64m -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -ea

default: simulation client

simulation: common
	javac $(compileopts) aoi/GameStateSender.java aoi/PlayerListener.java aoi/Simulation.java aoi/ClusterServerSide.java aoi/ClusterDatabase.java aoi/Server.java

client: common
	javac $(compileopts) aoi/Intelligence.java aoi/HigherBeing.java aoi/GUI.java aoi/Client.java aoi/ClientConfiguration.java aoi/Nonhuman.java aoi/TimeMachine.java aoi/MindReader.java aoi/Drawable.java aoi/DrawablePath.java aoi/DrawableShip.java aoi/DrawableTail.java aoi/DrawableScoringEffect.java aoi/DrawableBullets.java aoi/DrawUtils.java aoi/App.java

common:
	javac $(compileopts) aoi/PlayerState.java aoi/PlayerControlParameters.java aoi/GameState.java aoi/Bullet.java aoi/Cluster.java aoi/Arena.java aoi/SimulationUtils.java aoi/DrawableCluster.java aoi/NotStupidPolygon.java

doc:
	javadoc -d doc aoi

clean:
	rm -f *~ *.class aoi/*~ aoi/*.class stdlib/*~ stdlib/*.class
	rm -rf doc

run_serv:
	java $(runopts) -Xloggc:out/serv.mem aoi/Server < server.conf

run_client0:
	java $(runopts) -Xloggc:out/client0.mem aoi/Client < client0local.conf

run_client0r:
	java $(runopts) -Xloggc:out/client0.mem aoi/Client < client0remote.conf

run_client1:
	java $(runopts) -Xloggc:out/client1.mem aoi/Client < client1local.conf

run_client1r:
	java $(runopts) -Xloggc:out/client1.mem aoi/Client < client1remote.conf

jar:
	jar cfm aoi.jar Manifest.txt aoi/*.class client.conf
