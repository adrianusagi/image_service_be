FROM maven:3-eclipse-temurin-25

RUN apt-get update && apt-get install -y \
    libheif-examples \
    imagemagick \
    && rm -rf /var/lib/apt/lists/*