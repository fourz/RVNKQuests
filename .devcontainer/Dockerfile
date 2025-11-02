# Use Eclipse Temurin OpenJDK 21 as base image
# Minecraft 1.20.5+ requires Java 21
FROM eclipse-temurin:21-jdk-jammy

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Set up non-root user
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

# Install common dependencies and tools
RUN apt-get update && apt-get install -y \
    # Common utilities
    git \
    curl \
    wget \
    ca-certificates \
    gnupg \
    lsb-release \
    unzip \
    zip \
    tar \
    # Build essentials
    build-essential \
    # Network tools (useful for testing server connectivity)
    net-tools \
    iputils-ping \
    netcat \
    # Text editors
    nano \
    vim \
    # Process management
    procps \
    # JSON/YAML processing
    jq \
    # Clean up
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Maven
ARG MAVEN_VERSION=3.9.6
RUN wget https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven \
    && ln -s /opt/maven/bin/mvn /usr/bin/mvn \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Install Gradle
ARG GRADLE_VERSION=8.6
RUN wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip -d /opt gradle-${GRADLE_VERSION}-bin.zip \
    && ln -s /opt/gradle-${GRADLE_VERSION} /opt/gradle \
    && ln -s /opt/gradle/bin/gradle /usr/bin/gradle \
    && rm gradle-${GRADLE_VERSION}-bin.zip

# Set environment variables for Java, Maven, and Gradle
ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
ENV MAVEN_HOME=/opt/maven
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${GRADLE_HOME}/bin:${PATH}"

# Create non-root user if it doesn't exist
RUN groupadd --gid $USER_GID $USERNAME || true \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME -s /bin/bash || true \
    # Add sudo support
    && apt-get update \
    && apt-get install -y sudo \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create cache directories with proper permissions
RUN mkdir -p /home/$USERNAME/.m2 \
    && mkdir -p /home/$USERNAME/.gradle \
    && chown -R $USERNAME:$USERNAME /home/$USERNAME

# Verify Java installation
RUN java -version

# Switch to non-root user
USER $USERNAME

# Set working directory
WORKDIR /workspace

# Reset to interactive mode
ENV DEBIAN_FRONTEND=dialog

# Default command
CMD ["/bin/bash"]
