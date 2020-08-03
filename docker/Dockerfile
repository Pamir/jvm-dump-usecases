# our base build image
FROM maven:3.5-jdk-8 as maven

# copy the project files
COPY ./pom.xml ./pom.xml

# build all dependencies
RUN mvn dependency:go-offline -B

# copy your other files
COPY ./src ./src

# build for release
RUN mvn package

FROM gcr.io/distroless/java:8

# set deployment directory
WORKDIR /my-fault

# copy over the built artifact from the maven image
COPY --from=maven target/application.jar ./

# set the startup command to run your binary
ENTRYPOINT ["java", "-jar", "./target/application.jar"]
