## Env

```
docker-compose up -d
```

listing all topics
```
docker-compose exec kafka kafka-topics --bootstrap-server kafka:9093  --list
```

creating topic
```
docker-compose exec kafka kafka-topics  --create \
  --bootstrap-server kafka:9093 \
  --replication-factor 1 \
  --partitions 4 \
  --topic test-kafka-consumer
```

reading topic
```
kafkacat -C -b localhost:9092 -t test-kafka-consumer -f '%k:%s\n' -o beginning
```
or
```
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9093 \
  --topic test-kafka-consumer \
  --from-beginning
```

feeding  topic
```
echo 'tom#{ "name": "tom", "age": 12 }' | kafkacat -b localhost:9092 -t test-kafka-consumer -P -K'#'
```