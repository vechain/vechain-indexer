SHELL := /bin/bash

help:
	@egrep -h '\s#@\s' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?#@ "}; {printf "\033[36m  %-30s\033[0m %s\n", $$1, $$2}'

format: #@ Format the code with Spotless.
	./gradlew spotlessApply

# Application Build (Gradle)
build: format build-indexer build-api #@ Build the application with Gradle.
	echo "Build completed."
.PHONY:build
build-indexer: #@ Build the application with Gradle.
	./gradlew :package:indexer:build -x test
build-api: #@ Build the application with Gradle.
	./gradlew :package:api:build -x test

# Application Build (Docker)
build-image: build-image-indexer build-image-api #@ Build the application with Docker.
	echo "Build completed."
build-image-indexer: #@ Build the application with Docker.
	docker build --build-arg APP_VERSION=v.1.0.0 --build-arg PACKAGE_NAME=indexer -t veworld-indexer .
build-image-api: #@ Build the application with Docker.
	docker build --build-arg APP_VERSION=v.1.0.0 --build-arg PACKAGE_NAME=api -t veworld-api .
build-k6: #@ Build the K6 docker image.
	docker build --build-arg APP_VERSION=v.1.0.0 -t veworld-k6 load-testing

test: #@ Run all the tests.
	./gradlew cleanTest test
test-e2e: #@ Run all the end-to-end tests.
	./gradlew clean :package:e2e:test --stacktrace
test-api: #@ Run all the API tests.
	./gradlew clean :package:api:test
test-indexer: #@ Run all the indexer tests.
	./gradlew clean :package:indexer:test
test-common: #@ Run all the common tests.
	./gradlew clean :package:common:test

# Load Testing
LOAD_TEST_COMMAND=docker compose -f load-testing/docker-compose.yaml

load-test: #@ Run the load tests (all tests).
	$(LOAD_TEST_COMMAND) up --build -d --wait
	open http://localhost:3000/d/GlqvWKLVk/k6-load-testing-results\?orgId\=1\&refresh\=5s\&from\=now-5m\&to\=now
load-test-nfts: #@ Run only the NFTs test.
	$(LOAD_TEST_COMMAND) up --build -d --wait influxdb grafana k6-app-nfts k6-app-nfts-by-owner-contract k6-app-nft-contracts
	open http://localhost:3000/d/GlqvWKLVk/k6-load-testing-results\?orgId\=1\&refresh\=5s\&from\=now-5m\&to\=now
load-test-transactions: #@ Run only the Transactions test.
	$(LOAD_TEST_COMMAND) up --build -d --wait influxdb grafana k6-app-transactions-origin k6-app-transactions-delegator
	open http://localhost:3000/d/GlqvWKLVk/k6-load-testing-results\?orgId\=1\&refresh\=5s\&from\=now-5m\&to\=now
load-test-transfer-events: #@ Run only the Transfer Events test.
	$(LOAD_TEST_COMMAND) up --build -d --wait influxdb grafana k6-app-transfer-events-address k6-app-transfer-events-destination k6-app-transfer-events-origin k6-app-transfer-events-token-address k6-app-fungible-tokens-contracts-by-address
	open http://localhost:3000/d/GlqvWKLVk/k6-load-testing-results\?orgId\=1\&refresh\=5s\&from\=now-5m\&to\=now
load-test-history: #@ Run only the History test.
	$(LOAD_TEST_COMMAND) up --build -d --wait influxdb grafana k6-app-history
	open http://localhost:3000/d/GlqvWKLVk/k6-load-testing-results\?orgId\=1\&refresh\=5s\&from\=now-5m\&to\=now
load-test-clean: #@ Clean the load tests data.
	$(LOAD_TEST_COMMAND) down -v --remove-orphans

# Application Run (local)
run-indexer: build-indexer #@ Run the indexer locally.
	@set -a; \
	source ./packages/indexer/.env; \
	set +a; \
	java -jar packages/indexer/build/libs/indexer*.jar
run-api: build-api #@ Run the api locally.
	@set -a; \
        source ./packages/api/.env; \
        set +a; \
	java -jar packages/api/build/libs/api*.jar

# All
start: #@ Remove, clean and start all the infrastructure and the application.
	make db-up db-setup app-up
clean: #@ Clean all the infrastructure and the application data.
	make load-test-clean app-down db-clean load-test-clean
down: #@ Stop all the infrastructure and the application.
	make app-down db-down

# Application
app-up: format #@ Start the application.
	docker compose up -d --build --wait
app-down: #@ Stop the application.
	docker compose down
app-logs: #@ Attach to the application logs.
	docker compose logs -f

# Metrics
METRICS_COMMAND=docker compose -f metrics/compose.yaml

metrics-up: #@ Start Prometheus and Grafana.
	$(METRICS_COMMAND) up -d --wait
	@echo "Prometheus: http://localhost:9090"
	@echo "Grafana: http://localhost:3000 (admin/admin)"
metrics-down: #@ Stop Prometheus and Grafana.
	$(METRICS_COMMAND) down
metrics-clean: #@ Stop and remove all metrics data.
	$(METRICS_COMMAND) down -v --remove-orphans
metrics-logs: #@ Attach to the metrics logs.
	$(METRICS_COMMAND) logs -f
metrics-restart-grafana: #@ Restart only Grafana service.
	docker kill grafana; docker rm grafana; docker volume rm metrics_grafana_data; make metrics-up
