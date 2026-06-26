.PHONY: all clean build test run bench

# Variables
MVN = mvn
JAR = target/titan-chess-engine-1.0.0-SNAPSHOT.jar

all: build

clean:
	$(MVN) clean

build:
	$(MVN) package

test:
	$(MVN) test

run: build
	java -jar $(JAR)

bench: build
	java -jar $(JAR) bench 8
