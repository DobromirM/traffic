#Dockerfile

FROM openjdk:11-jdk-stretch

WORKDIR /

EXPOSE 9001

COPY dist/swim-traffic-3.11.0 /app/swim-traffic-3.11.0/
COPY dist/swim-traffic-3.11.0/ui/ /app/swim-traffic-3.11.0/ui

WORKDIR /app/swim-traffic-3.11.0/bin
ENTRYPOINT ["./swim-traffic"]
