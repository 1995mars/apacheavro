# Docker Compose Kafka KRaft 3 Brokers + Schema Registry + Kafdrop (Confluent 7.6.0)

Tài liệu này cung cấp **docker-compose.yml** chạy:

- Kafka 3 brokers **KRaft mode** (không ZooKeeper)
- Schema Registry
- Kafdrop UI

> Lưu ý quan trọng:
> - Kafka KRaft cần **CLUSTER_ID** cố định.
> - Schema Registry **không dùng** `INTERNAL://` trong bootstrap servers; phải dùng `PLAINTEXT://...` hoặc `host:port` tương ứng protocol.

---

## 1) Tạo CLUSTER_ID (bắt buộc)

Chạy 1 trong 2 cách:

**Cách 1 (Docker):**
```bash
docker run --rm confluentinc/cp-kafka:7.6.0 bash -lc "kafka-storage.sh random-uuid"
```

**Cách 2 (nếu đã có kafka-storage.sh trên máy):**
```bash
kafka-storage.sh random-uuid
```

Copy giá trị UUID và tạo file `.env` cùng thư mục với `docker-compose.yml`:

```env
CLUSTER_ID=REPLACE_WITH_YOUR_CLUSTER_ID
```

---

## 2) docker-compose.yml (KRaft 3 nodes)

Lưu nội dung sau thành **docker-compose.yml**:

```yaml
version: "3.7"

services:
  kafka1:
    image: confluentinc/cp-kafka:7.6.0
    container_name: kafka1
    hostname: kafka1
    ports:
      - "9092:19092"
    environment:
      CLUSTER_ID: "${CLUSTER_ID}"

      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: "broker,controller"
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka1:9093,2@kafka2:9093,3@kafka3:9093"

      # Listeners: INTERNAL (docker) + EXTERNAL (host) + CONTROLLER (kraft)
      KAFKA_LISTENERS: "INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:19092,CONTROLLER://0.0.0.0:9093"
      KAFKA_ADVERTISED_LISTENERS: "INTERNAL://kafka1:9092,EXTERNAL://localhost:9092"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_INTER_BROKER_LISTENER_NAME: "INTERNAL"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"

      # Cluster safety (3 nodes)
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2

      # Dev convenience
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka1_data:/var/lib/kafka/data

  kafka2:
    image: confluentinc/cp-kafka:7.6.0
    container_name: kafka2
    hostname: kafka2
    ports:
      - "9093:19092"
    environment:
      CLUSTER_ID: "${CLUSTER_ID}"

      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: "broker,controller"
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka1:9093,2@kafka2:9093,3@kafka3:9093"

      KAFKA_LISTENERS: "INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:19092,CONTROLLER://0.0.0.0:9093"
      KAFKA_ADVERTISED_LISTENERS: "INTERNAL://kafka2:9092,EXTERNAL://localhost:9093"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_INTER_BROKER_LISTENER_NAME: "INTERNAL"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"

      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2

      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka2_data:/var/lib/kafka/data

  kafka3:
    image: confluentinc/cp-kafka:7.6.0
    container_name: kafka3
    hostname: kafka3
    ports:
      - "9094:19092"
    environment:
      CLUSTER_ID: "${CLUSTER_ID}"

      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: "broker,controller"
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka1:9093,2@kafka2:9093,3@kafka3:9093"

      KAFKA_LISTENERS: "INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:19092,CONTROLLER://0.0.0.0:9093"
      KAFKA_ADVERTISED_LISTENERS: "INTERNAL://kafka3:9092,EXTERNAL://localhost:9094"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_INTER_BROKER_LISTENER_NAME: "INTERNAL"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"

      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2

      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka3_data:/var/lib/kafka/data

  schema-registry:
    image: confluentinc/cp-schema-registry:7.6.0
    container_name: schema-registry
    hostname: schema-registry
    depends_on:
      - kafka1
      - kafka2
      - kafka3
    ports:
      - "8081:8081"
    environment:
      SCHEMA_REGISTRY_HOST_NAME: "schema-registry"
      SCHEMA_REGISTRY_LISTENERS: "http://0.0.0.0:8081"

      # IMPORTANT: dùng PLAINTEXT:// để khớp kafkastore.security.protocol=PLAINTEXT
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: "PLAINTEXT://kafka1:9092,PLAINTEXT://kafka2:9092,PLAINTEXT://kafka3:9092"

  kafdrop:
    image: obsidiandynamics/kafdrop:latest
    container_name: kafdrop
    depends_on:
      - kafka1
      - kafka2
      - kafka3
    ports:
      - "9000:9000"
    environment:
      KAFKA_BROKERCONNECT: "kafka1:9092,kafka2:9092,kafka3:9092"

volumes:
  kafka1_data:
  kafka2_data:
  kafka3_data:
```

---

## 3) Chạy cluster

```bash
docker compose up -d
```

Kiểm tra:

```bash
docker compose ps
docker logs -f kafka1
docker logs -f schema-registry
```

Truy cập:
- Kafdrop: http://localhost:9000
- Schema Registry: http://localhost:8081

---

## 4) Spring Boot cấu hình gợi ý

### Nếu app chạy **ngoài docker** (local Windows/macOS/Linux)
```yaml
spring:
  kafka:
    bootstrap-servers: "localhost:9092,localhost:9093,localhost:9094"
    properties:
      schema.registry.url: "http://localhost:8081"
```

### Nếu app chạy **trong docker network** (cùng compose)
```yaml
spring:
  kafka:
    bootstrap-servers: "kafka1:9092,kafka2:9092,kafka3:9092"
    properties:
      schema.registry.url: "http://schema-registry:8081"
```

---

## 5) Note
- Chỉ cần chạy docker-compose up vì đã tạo sẵn file docker-compose
- vào thư mục postman để lấy api để test
---
