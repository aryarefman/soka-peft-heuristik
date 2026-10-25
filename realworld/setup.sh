#!/usr/bin/env bash
# Membangun infrastruktur cloud nyata untuk demo (jalankan dari folder realworld/).
#   1 datacenter (cluster kind "soka-dc") -> 2 host (worker node) -> 2 VM per host (pod)
set -euo pipefail
cd "$(dirname "$0")"

echo "== 1. Membuat datacenter (cluster Kubernetes) =="
if ! kind get clusters | grep -qx soka-dc; then
  kind create cluster --config k8s/kind-cluster.yaml --wait 120s
fi

echo "== 2. Membatasi kapasitas CPU tiap host =="
docker update --cpus 1 soka-dc-worker  >/dev/null   # host-1 Standard
docker update --cpus 2 soka-dc-worker2 >/dev/null   # host-2 Premium
kubectl get nodes -L host,tier

echo "== 3. Mengunduh image VM (python:3.12-slim) di tiap host =="
for node in soka-dc-worker soka-dc-worker2; do
  docker exec "$node" crictl pull python:3.12-slim >/dev/null
done

echo "== 4. Menjalankan 4 VM (2 per host) =="
kubectl create configmap worker-code --from-file=worker/worker.py --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/vms.yaml
kubectl wait --for=condition=Ready pod -l app=soka-vm --timeout=180s
kubectl get pods -o wide

echo "== Siap. Jalankan broker: java broker/RealWorldBroker.java ALL =="
