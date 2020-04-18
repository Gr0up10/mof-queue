FROM mozilla/sbt:latest
RUN sbt version
COPY . .
RUN sbt compile
CMD sbt run