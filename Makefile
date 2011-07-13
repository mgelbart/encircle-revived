SHELL := /bin/bash

package := aoi

srcdir := src/$(package)/

classpath := .:lib/commons-codec-1.5.jar:bin/

compileopts := -g -classpath $(classpath) -d bin/

runopts := -classpath $(classpath) -Xmx64m -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -ea

serverFiles := $(srcdir)GameStateSender.java $(srcdir)PlayerListener.java $(srcdir)Simulation.java $(srcdir)ClusterServerSide.java $(srcdir)ClusterDatabase.java $(srcdir)Server.java

clientFiles := $(srcdir)Client.java $(srcdir)Intelligence.java $(srcdir)HigherBeing.java $(srcdir)GUI.java $(srcdir)ClientConfiguration.java $(srcdir)Nonhuman.java $(srcdir)TimeMachine.java $(srcdir)MindReader.java $(srcdir)DefaultDrawable.java $(srcdir)DrawableShip.java $(srcdir)DrawableTail.java $(srcdir)DrawableScoringEffect.java $(srcdir)DrawableScore.java $(srcdir)DrawableBullets.java $(srcdir)App.java

commonFiles := $(srcdir)PlayerState.java $(srcdir)Ship.java $(srcdir)PlayerControlParameters.java $(srcdir)GameState.java $(srcdir)Bullet.java $(srcdir)Cluster.java $(srcdir)Tail.java $(srcdir)NSPath.java $(srcdir)Arena.java $(srcdir)SimulationUtils.java $(srcdir)Drawable.java $(srcdir)DrawUtils.java $(srcdir)DrawableCluster.java $(srcdir)NotStupidPolygon.java $(srcdir)ImageMetaData.java $(srcdir)Logger.java

default: server client

dirs:
	mkdir -p out bin segmentation/game_output/fly

server: common
	javac $(compileopts) $(serverFiles)

client: common
	javac $(compileopts) $(clientFiles)

common: dirs
	javac $(compileopts) $(commonFiles)

all:	common server client

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
	jar cfm aoi.jar Manifest.txt client.conf -C bin/ aoi

push_jar: jar
	scp aoi.jar aoiserv@vps6609.inmotionhosting.com:public_html/

push_bin:
	scp -r bin aoiserv@vps6609.inmotionhosting.com:aoirun/
