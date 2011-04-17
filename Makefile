SHELL := /bin/bash

default: simulation client

simulation: common
	javac -g SConGameStateSender.java SConPlayerListener.java SConSimulation.java

client: common
	javac -g SConParsedGameState.java SConIntelligence.java HigherBeing.java SConClient.java

common: SConPlayerState.java SConPlayerControlParameters.java
	javac -g SConPlayerState.java SConPlayerControlParameters.java

clean:
	rm -f *~ *.class

run_serv:
	java SConSimulation

run_client1:
	echo -e "1\ntrue" | java SConClient

run_client2:
	echo -e "2\nfalse" | java SConClient
