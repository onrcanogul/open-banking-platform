# Kubernetes

Runs on the Kubernetes of Docker Desktop. Oracle, Kafka and Redis stay in Compose.
Only the gateway, the identity server and the panel are reachable from outside the cluster.

Build the images from the repository root, with one line for each service:

```bash
docker build --build-arg MODULE=api-gateway -t obe/api-gateway:0.1.0 .
docker build -t obe/operations-ui:0.1.0 frontend/operations-ui
```

Create the namespace and the secret, which comes from `.env` and is never committed:

```bash
kubectl apply -f deploy/k8s/00-common.yaml
tr -d '\r' < .env > .env.k8s
kubectl -n obe create secret generic obe-secrets --from-env-file=.env.k8s --dry-run=client -o yaml | kubectl apply -f -
rm .env.k8s
```

Then deploy:

```bash
kubectl apply -f deploy/k8s/
kubectl -n obe get pods
```
