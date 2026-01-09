# Kafka KRaft 3 Brokers trên Kubernetes (Confluent 7.6.0)

Tài liệu này cung cấp manifest Kubernetes để chạy:

- Kafka 3 brokers **KRaft mode** (không ZooKeeper)
- Schema Registry
- Kafdrop UI
- StatefulSet + Headless Service (DNS ổn định cho broker)

> Gợi ý: Bạn có thể gom tất cả YAML trong tài liệu này vào **một file** `kafka-kraft-k8s.yaml` rồi `kubectl apply -f kafka-kraft-k8s.yaml`.

---

## 1) Namespace (tuỳ chọn)

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: kafka-demo
```

---

## 2) CLUSTER_ID (bắt buộc)

Kafka KRaft cần `CLUSTER_ID` cố định cho cluster.

### Cách lấy CLUSTER_ID (chọn 1)

**Cách A (nếu bạn có Kafka binary trên máy):**
```bash
kafka-storage.sh random-uuid
```

**Cách B (chạy tạm 1 container để lấy UUID):**
```bash
docker run --rm confluentinc/cp-kafka:7.6.0 bash -lc "kafka-storage.sh random-uuid"
```

Sau đó dán vào ConfigMap dưới đây:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: kafka-config
  namespace: kafka-demo
data:
  CLUSTER_ID: "REPLACE_WITH_YOUR_CLUSTER_ID"
```

---

## 3) Headless Service cho Kafka

Headless service giúp các broker có DNS ổn định:

- `kafka-0.kafka.kafka-demo.svc.cluster.local`
- `kafka-1.kafka.kafka-demo.svc.cluster.local`
- `kafka-2.kafka.kafka-demo.svc.cluster.local`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: kafka
  namespace: kafka-demo
spec:
  clusterIP: None
  selector:
    app: kafka
  ports:
    - name: internal
      port: 9092
      targetPort: 9092
    - name: controller
      port: 9093
      targetPort: 9093
```

---

## 4) Kafka StatefulSet (3 brokers, KRaft)

- Listener nội bộ: `INTERNAL://:9092`
- Listener controller: `CONTROLLER://:9093`
- `advertised.listeners` dùng DNS của Pod (quan trọng để tránh lỗi `NOT_COORDINATOR`)

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  namespace: kafka-demo
spec:
  serviceName: kafka
  replicas: 3
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
        - name: kafka
          image: confluentinc/cp-kafka:7.6.0
          ports:
            - containerPort: 9092
              name: internal
            - containerPort: 9093
              name: controller
          env:
            - name: CLUSTER_ID
              valueFrom:
                configMapKeyRef:
                  name: kafka-config
                  key: CLUSTER_ID

            - name: KAFKA_PROCESS_ROLES
              value: "broker,controller"

            - name: KAFKA_CONTROLLER_LISTENER_NAMES
              value: "CONTROLLER"

            - name: KAFKA_INTER_BROKER_LISTENER_NAME
              value: "INTERNAL"

            - name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
              value: "INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"

            - name: KAFKA_LISTENERS
              value: "INTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093"

            - name: KAFKA_CONTROLLER_QUORUM_VOTERS
              value: "0@kafka-0.kafka.kafka-demo.svc.cluster.local:9093,1@kafka-1.kafka.kafka-demo.svc.cluster.local:9093,2@kafka-2.kafka.kafka-demo.svc.cluster.local:9093"

            - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
              value: "2"
            - name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR
              value: "2"
            - name: KAFKA_TRANSACTION_STATE_LOG_MIN_ISR
              value: "2"

            - name: KAFKA_AUTO_CREATE_TOPICS_ENABLE
              value: "true"

          # Convert hostname kafka-0 -> node.id=0; set advertised.listeners theo DNS pod
          command:
            - bash
            - -ec
            - |
              POD_NAME="${HOSTNAME}"            # kafka-0
              ORDINAL="${POD_NAME##*-}"         # 0
              export KAFKA_NODE_ID="${ORDINAL}"
              export KAFKA_ADVERTISED_LISTENERS="INTERNAL://${POD_NAME}.kafka.kafka-demo.svc.cluster.local:9092"
              echo "NODE_ID=${KAFKA_NODE_ID}"
              echo "ADVERTISED=${KAFKA_ADVERTISED_LISTENERS}"
              /etc/confluent/docker/run

          volumeMounts:
            - name: data
              mountPath: /var/lib/kafka/data

  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
```

> Ghi chú:
> - `storage: 10Gi` cần StorageClass/PV phù hợp cluster của bạn (minikube/kind/k3s/cluster thật).

---

## 5) Schema Registry

Schema Registry cần `kafkastore.bootstrap.servers` có endpoint đúng `PLAINTEXT://...` (không dùng `INTERNAL://`).

### Service + Deployment

```yaml
apiVersion: v1
kind: Service
metadata:
  name: schema-registry
  namespace: kafka-demo
spec:
  selector:
    app: schema-registry
  ports:
    - name: http
      port: 8081
      targetPort: 8081
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: schema-registry
  namespace: kafka-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: schema-registry
  template:
    metadata:
      labels:
        app: schema-registry
    spec:
      containers:
        - name: schema-registry
          image: confluentinc/cp-schema-registry:7.6.0
          ports:
            - containerPort: 8081
              name: http
          env:
            - name: SCHEMA_REGISTRY_HOST_NAME
              value: "schema-registry"
            - name: SCHEMA_REGISTRY_LISTENERS
              value: "http://0.0.0.0:8081"
            - name: SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS
              value: "PLAINTEXT://kafka-0.kafka.kafka-demo.svc.cluster.local:9092,PLAINTEXT://kafka-1.kafka.kafka-demo.svc.cluster.local:9092,PLAINTEXT://kafka-2.kafka.kafka-demo.svc.cluster.local:9092"
```

---

## 6) Kafdrop UI

### Service + Deployment

```yaml
apiVersion: v1
kind: Service
metadata:
  name: kafdrop
  namespace: kafka-demo
spec:
  selector:
    app: kafdrop
  ports:
    - name: http
      port: 9000
      targetPort: 9000
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafdrop
  namespace: kafka-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: kafdrop
  template:
    metadata:
      labels:
        app: kafdrop
    spec:
      containers:
        - name: kafdrop
          image: obsidiandynamics/kafdrop:latest
          ports:
            - containerPort: 9000
              name: http
          env:
            - name: KAFKA_BROKERCONNECT
              value: "kafka-0.kafka.kafka-demo.svc.cluster.local:9092,kafka-1.kafka.kafka-demo.svc.cluster.local:9092,kafka-2.kafka.kafka-demo.svc.cluster.local:9092"
```

---

## 7) Apply

Nếu bạn gom tất cả manifest vào `kafka-kraft-k8s.yaml`:

```bash
kubectl apply -f kafka-kraft-k8s.yaml
```

Kiểm tra:

```bash
kubectl -n kafka-demo get pods
kubectl -n kafka-demo get svc
```

---

## 8) Port-forward để truy cập từ máy local

```bash
kubectl -n kafka-demo port-forward svc:kafdrop 9000:9000
kubectl -n kafka-demo port-forward svc:schema-registry 8081:8081
```

- Kafdrop: http://localhost:9000
- Schema Registry: http://localhost:8081

---

## 9) Spring Boot cấu hình khi chạy trong Kubernetes

```yaml
spring:
  kafka:
    bootstrap-servers: "kafka-0.kafka.kafka-demo.svc.cluster.local:9092,kafka-1.kafka.kafka-demo.svc.cluster.local:9092,kafka-2.kafka.kafka-demo.svc.cluster.local:9092"
    properties:
      schema.registry.url: "http://schema-registry.kafka-demo.svc.cluster.local:8081"
```

---

## 10) Troubleshooting nhanh

### A) Consumer bị loop `NOT_COORDINATOR` / rebalance liên tục
- Nguyên nhân thường do `advertised.listeners` không đúng DNS/port.
- Manifest trên đã set `KAFKA_ADVERTISED_LISTENERS` theo DNS pod.

### B) Schema Registry báo:
`No supported Kafka endpoints are configured...`
- Nguyên nhân: dùng endpoint không khớp protocol.
- Sửa: `SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS` phải là `PLAINTEXT://...`

### C) PVC Pending
- Cluster của bạn chưa có StorageClass mặc định hoặc PV.
- Với minikube/k3s thường ok; với kind đôi khi cần cài provisioner.

---
