SHELL := /bin/bash

default: simulation client

simulation: common
	javac -g SConGameStateSender.java SConPlayerListener.java SConSimulation.java

client: common
	javac -g SConIntelligence.java HigherBeing.java SConClient.java

common: SConPlayerState.java SConPlayerControlParameters.java SConGameState.java
	javac -g SConPlayerState.java SConPlayerControlParameters.java SConGameState.java

clean:
	rm -f *~ *.class

run_serv:
	java -ea SConSimulation

run_client0:
	echo -e "0\ntrue" | java -ea SConClient

run_client1:
	echo -e "1\nfalse" | java -ea SConClient
