FROM atlassian/bamboo-agent-base:12.1.10-ubuntu

ARG NODE_VERSION=24.15.0

USER root

# Docker is only a client here: builds use the host's engine through the mounted socket
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io git curl xz-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# The mounted socket belongs to the root group, and the agent itself runs as bamboo
RUN usermod -aG root bamboo

RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
    | tar -xJ -C /usr/local --strip-components=1
