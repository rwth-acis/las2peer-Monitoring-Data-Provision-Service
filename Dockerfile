FROM openjdk:17-jdk-alpine

ENV HTTP_PORT=8080
ENV HTTPS_PORT=8443
ENV LAS2PEER_PORT=9011

RUN apk add --update bash curl dos2unix tzdata  xmlstarlet  tini mysql-client && rm -f /var/cache/apk/*

RUN addgroup -g 1000 -S las2peer && \
    adduser -u 1000 -S las2peer -G las2peer

COPY --chown=las2peer:las2peer . /src
WORKDIR /src
RUN dos2unix ./gradlew
# run the rest as unprivileged user
USER las2peer
RUN chmod +x gradlew && ./gradlew build --exclude-task test


RUN dos2unix /src/docker-entrypoint.sh
RUN dos2unix /src/gradle.properties
EXPOSE $HTTP_PORT
EXPOSE $HTTPS_PORT
EXPOSE $LAS2PEER_PORT
ENTRYPOINT ["/src/docker-entrypoint.sh"]
