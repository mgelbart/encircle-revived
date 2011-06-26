SHELL := /bin/bash

package := aoi

srcdir := src/$(package)/

classpath := .:lib/commons-codec-1.5.jar:bin/

compileopts := -g -classpath $(classpath) -d bin/

runopts := -classpath $(classpath) -Xmx64m -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -ea

serverFiles := $(srcdir)GameStateSender.java $(srcdir)PlayerListener.java $(srcdir)Simulation.java $(srcdir)ClusterServerSide.java $(srcdir)ClusterDatabase.java $(srcdir)Server.java

clientFiles := $(srcdir)Client.java $(srcdir)Intelligence.java $(srcdir)HigherBeing.java $(srcdir)GUI.java $(srcdir)ClientConfiguration.java $(srcdir)Nonhuman.java $(srcdir)TimeMachine.java $(srcdir)MindReader.java $(srcdir)DrawablePath.java $(srcdir)DrawableShip.java $(srcdir)DrawableTail.java $(srcdir)DrawableScoringEffect.java $(srcdir)DrawableScore.java $(srcdir)DrawableBullets.java $(srcdir)DrawUtils.java $(srcdir)App.java

commonFiles := $(srcdir)PlayerState.java $(srcdir)PlayerControlParameters.java $(srcdir)GameState.java $(srcdir)Bullet.java $(srcdir)Cluster.java $(srcdir)Tail.java $(srcdir)Arena.java $(srcdir)SimulationUtils.java $(srcdir)Drawable.java $(srcdir)DrawableCluster.java $(srcdir)NotStupidPolygon.java $(srcdir)ImageMetaData.java

default: server client

server:
	javac $(compileopts) $(serverFiles) $(commonFiles)

client:
	javac $(compileopts) $(clientFiles) $(commonFiles)

common:
	javac $(compileopts) $(commonFiles)

all:
	javac $(compileopts) $(serverFiles) $(clientFiles) $(commonFiles)

doc:
	javadoc -d doc aoi

clean:
	rm -f *~ *.class $(srcdir)*~ bin/aoi/*~ bin/aoi/*.class stdlib/*~ stdlib/*.class
	rm -rf doc

run_serv:
	java $(runopts) -Xloggc:out/serv.mem $(package).Server < server.conf

run_client0:
	java $(runopts) -Xloggc:out/client0.mem $(package).Client < client0local.conf

run_client0r:
	java $(runopts) -Xloggc:out/client0.mem $(package).Client < client0remote.conf

run_client1:
	java $(runopts) -Xloggc:out/client1.mem $(package).Client < client1local.conf

run_client1r:
	java $(runopts) -Xloggc:out/client1.mem $(package).Client < client1remote.conf

jar:
	jar cfm aoi.jar Manifest.txt client0remote.conf -C bin/ aoi
