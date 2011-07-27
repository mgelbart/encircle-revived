SHELL := /bin/bash

package := aoi

srcdir := src/$(package)/
streamsrcdir := src/unreliablestream/

classpath := .:lib/commons-codec-1.5.jar:bin/

compileopts := -g -classpath $(classpath) -d bin/

runopts := -classpath $(classpath) -Xmx64m -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -ea

serverFiles := $(srcdir)Communicator.java $(srcdir)Simulation.java $(srcdir)ClusterServerSide.java $(srcdir)ClusterDatabase.java $(srcdir)Server.java

clientFiles := $(srcdir)Client.java $(srcdir)Intelligence.java $(srcdir)HigherBeing.java $(srcdir)GUI.java $(srcdir)ClientConfiguration.java $(srcdir)Nonhuman.java $(srcdir)TimeMachine.java $(srcdir)MindReader.java $(srcdir)DefaultDrawable.java $(srcdir)DrawableFPS.java $(srcdir)DrawableShip.java $(srcdir)DrawableTail.java $(srcdir)ScoringEffect.java $(srcdir)DrawableScoreboard.java $(srcdir)DrawableBullets.java $(srcdir)App.java $(srcdir)StdAudio.java $(srcdir)Audible.java $(srcdir)AudibleShot.java $(srcdir)AudibleCrash.java $(srcdir)KeyboardPilot.java $(srcdir)MousePilot.java $(srcdir)Shooter.java $(srcdir)DrawablePath.java $(srcdir)Tracker.java $(srcdir)DefaultIntelligence.java $(srcdir)DefaultIntelligenceWithGUI.java

commonFiles := $(srcdir)Angle.java $(srcdir)PlayerState.java $(srcdir)Ship.java $(srcdir)PlayerControlParameters.java $(srcdir)GameState.java $(srcdir)Bullet.java $(srcdir)Cluster.java $(srcdir)Tail.java $(srcdir)NSPath.java $(srcdir)Arena.java $(srcdir)SimulationUtils.java $(srcdir)Drawable.java $(srcdir)DrawUtils.java $(srcdir)DrawableCluster.java $(srcdir)NSPolygon.java $(srcdir)ImageMetaData.java $(srcdir)Logger.java $(srcdir)CropEvent.java $(srcdir)CropEventGroup.java $(srcdir)Protocol.java

UDPFiles := $(streamsrcdir)UnreliableStream.java $(streamsrcdir)UnreliableStreamServer.java

default:
	javac $(compileopts) $(serverFiles) $(clientFiles) $(commonFiles) $(UDPFiles)

dirs:
	mkdir -p out bin segmentation/game_output/fly

server: common
	javac $(compileopts) $(serverFiles)

client: common
	javac $(compileopts) $(clientFiles)

common: dirs unreliablestream
	javac $(compileopts) $(commonFiles)

unreliablestream:
	javac $(compileopts) $(streamsrcdir)

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
	jar cfm aoi.jar Manifest.txt client.conf -C bin/ .

push_jar: jar
	scp aoi.jar ec2-user@ec2-50-17-115-154.compute-1.amazonaws.com:/var/www/html/

push_bin:
	scp -i ~/.ssh/encircle.pem -r bin server.conf ec2-user@ec2-50-17-115-154.compute-1.amazonaws.com:aoirun/

test_stream: unreliablestream
	java $(runopts) unreliablestream/UnreliableStreamServer

test_protocol: default
	java $(runopts) aoi/Protocol
