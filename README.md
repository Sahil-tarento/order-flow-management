# OrderFlow - OrderFlowManagement

OrderFlow is an enterprise-grade microservice built with Java 21 and Spring Boot demonstrating:
- Hexagonal architecture
- Event sourcing
- Saga orchestration (Temporal)
- Kafka-based event-driven design
- Redis-based idempotency and distributed locks
- Resilience patterns (Resilience4j)

Quick start (development):
1. Start dependencies: docker-compose up -d
2. Apply DDL: psql -h localhost -U orderflow_user -d orderflow -f src/main/resources/db/event-store-ddl.sql
3. Build and run the service: mvn -DskipTests package && java -jar target/*.jar
4. Swagger UI: http://localhost:8080/swagger-ui.html
5. Temporal UI: http://localhost:8088

Design decisions, diagrams, and interview talking points are included in the docs/ folder.
