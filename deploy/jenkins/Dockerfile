FROM jenkins/jenkins:2.580.2-jdk21

ARG NODE_VERSION=24.15.0

USER root

# Docker is only a client here: builds use the host's engine through the mounted socket
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
    | tar -xJ -C /usr/local --strip-components=1

# The mounted socket belongs to the root group, and the repository to another user
RUN usermod -aG root jenkins \
    && git config --system --add safe.directory /repo.git

COPY jenkins.yaml /usr/share/jenkins/ref/jenkins.yaml

USER jenkins

RUN jenkins-plugin-cli --plugins workflow-aggregator git junit configuration-as-code job-dsl
