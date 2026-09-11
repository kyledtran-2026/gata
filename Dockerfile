FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:latest

USER root

RUN mkdir -p /var/gata/uploads && \
    chown -R 185:0 /var/gata && \
    chmod -R g+w /var/gata

# Switch back to the non-root user
USER 185

# Optional: expose port (Spring Boot default)
#EXPOSE 6480

# Copy the JAR
ARG JAR_FILE
COPY ${JAR_FILE} /app.jar

# Better ENTRYPOINT - allows passing JAVA_OPTS easily
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app.jar"]