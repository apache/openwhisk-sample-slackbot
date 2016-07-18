# Use this Dockerfile to package the Slackbot in a container.
# Note that currently, src/main/resources/application.conf needs to exist and be configured before building the image.
FROM java:openjdk-8-jdk

RUN wget https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.13.11/sbt-launch.jar

ENV SBT_OPTS "-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled"

# Do this first to get all the sbt stuff downloaded and cached.
RUN java $SBT_OPTS -jar sbt-launch.jar tasks

ADD . project

WORKDIR project

RUN java $SBT_OPTS -jar ../sbt-launch.jar compile

CMD [ "java", "-jar", "../sbt-launch.jar", "run" ]
