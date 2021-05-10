mvn clean package && docker buildx build --platform linux/amd64,linux/arm64 -t rdomloge/catholicon-ms-sync-multiarch:latest . --push
