.PHONY: package test run clean native

# Maven must run on JDK 25 (JAVA_HOME must point at a JDK 25 install).
MVN ?= mvn
JAVA_BIN ?= $(JAVA_HOME)/bin/java

# Build the runnable fat jar: target/mellow.jar
package:
	$(MVN) -q -DskipTests package

# Run the unit test suite
test:
	$(MVN) test

# Build and run interactively
run: package
	$(JAVA_BIN) -jar target/mellow.jar

# GraalVM native image (requires GraalVM for JDK 25)
native:
	$(MVN) -Pnative package

clean:
	$(MVN) -q clean