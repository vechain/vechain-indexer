SHELL := /bin/bash

help:
	@egrep -h '\s#@\s' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?#@ "}; {printf "\033[36m  %-30s\033[0m %s\n", $$1, $$2}'

format: #@ Format the code with Spotless.
	./gradlew spotlessApply
	$(MAKE) format-json

format-json: #@ Format JSON dashboard files with jq.
	@for f in metrics/datadog/*.json metrics/grafana/provisioning/dashboards/*.json; do \
		if [ -f "$$f" ]; then \
			jq -S '.' "$$f" > "$$f.tmp" && mv "$$f.tmp" "$$f"; \
		fi; \
	done

# Application Build (Gradle)
build: format build-indexer build-api #@ Build the application with Gradle.
	echo "Build completed."
.PHONY:build
build-indexer: #@ Build the application with Gradle.
	./gradlew :package:indexer:build -x test
build-api: #@ Build the application with Gradle.
	./gradlew :package:api:build -x test

# Application Build (Docker)
comma := ,
GRADLE_SECRET := $(if $(wildcard $(HOME)/.gradle/gradle.properties),--secret id=gradle_props$(comma)src=$(HOME)/.gradle/gradle.properties,)
build-image: build-image-indexer build-image-api #@ Build the application with Docker.
	echo "Build completed."
build-image-indexer: #@ Build the application with Docker.
	docker build $(GRADLE_SECRET) --build-arg APP_VERSION=v.1.0.0 --build-arg PACKAGE_NAME=indexer -t veworld-indexer .
build-image-api: #@ Build the application with Docker.
	docker build $(GRADLE_SECRET) --build-arg APP_VERSION=v.1.0.0 --build-arg PACKAGE_NAME=api -t veworld-api .
build-k6: #@ Build the K6 docker image.
	docker build --build-arg APP_VERSION=v.1.0.0 -t veworld-k6 load-testing

test: #@ Run all the tests (excluding e2e).
	./gradlew cleanTest test -x :packages:e2e:test
test-ci: #@ Run all tests for CI (with caching, excludes e2e).
	./gradlew test -x :packages:e2e:test
test-e2e: #@ Run all the end-to-end tests.
	./gradlew :packages:e2e:test --stacktrace
test-api: #@ Run all the API tests.
	./gradlew clean :package:api:test
test-indexer: #@ Run all the indexer tests.
	./gradlew clean :package:indexer:test
test-common: #@ Run all the common tests.
	./gradlew clean :package:common:test

# API Schema Tests (Schemathesis via Docker)
SCHEMA_TEST_BASE_URL ?= http://host.docker.internal:8080
SCHEMA_TEST_MAX_EXAMPLES ?= 200
SCHEMA_TEST_MAX_RESPONSE_TIME_SECONDS ?= 2

test-api-schema: #@ Run API schema tests via Docker against a running API (default: localhost:8080).
	docker run --rm \
		--add-host=host.docker.internal:host-gateway \
		schemathesis/schemathesis:stable \
		run "$(SCHEMA_TEST_BASE_URL)/api-docs" \
		--url="$(SCHEMA_TEST_BASE_URL)" \
		--generation-deterministic \
		--max-examples=$(SCHEMA_TEST_MAX_EXAMPLES) \
		--checks=status_code_conformance,not_a_server_error,content_type_conformance \
		--phases=examples,coverage,fuzzing,stateful \
		--max-response-time=$(SCHEMA_TEST_MAX_RESPONSE_TIME_SECONDS)

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

# Datadog
DD_SCRIPT=python3 metrics/datadog/scripts/manage_pipeline.py

dd-get-pipeline: #@ Fetch Datadog pipeline config.
	$(DD_SCRIPT) get
dd-get-dashboard: #@ Fetch Datadog dashboard config.
	$(DD_SCRIPT) get-dashboard
dd-generate-openapi: #@ Generate OpenAPI spec from API with embedded MongoDB.
	./gradlew :packages:api:generateOpenApiSpec
dd-push-pipeline: #@ Push pipeline config to Datadog.
	$(DD_SCRIPT) push-pipeline
dd-push-dashboard: #@ Push dashboard config to Datadog.
	$(DD_SCRIPT) push-dashboard
dd-update-categories: #@ Update pipeline categories from api-docs.json.
	$(DD_SCRIPT) update-categories
dd-validate-categories: #@ Validate pipeline categories match api-docs.json.
	$(DD_SCRIPT) validate-categories
dd-sync: dd-get-pipeline dd-get-dashboard #@ Fetch pipeline and dashboard from Datadog.
dd-push: dd-push-pipeline dd-push-dashboard #@ Push pipeline and dashboard to Datadog.

# Database
DB_COMMAND=docker compose -f database/docker-compose-mongo.yaml
DB_MAKE_KEY=mkdir -p database/keys && [ -f database/keys/keyfile ] || openssl rand -base64 756 > database/keys/keyfile
DB_REMOVE_KEY=rm -f -R database/keys
DB_SETUP_COMMAND=docker compose -f database/docker-compose-mongo-setup.yaml
MONGO_URL=mongodb://indexer:password@localhost:27017/vechain?authSource=admin
BACKUP_DIR ?= $(PWD)/database/backups

db-all: #@ Remove, clean and start the database (Docker).
	make db-clean db-up db-setup
db-up: db-keyfile-create #@ Start all the database (Docker)
	$(DB_COMMAND) up -d --wait
db-setup: #@ Setup all the database (Docker)
	$(DB_SETUP_COMMAND) up --build; $(DB_SETUP_COMMAND) rm --force
db-clean: #@ Clean all the database data (Docker)
	$(DB_COMMAND) down -v --remove-orphans;
db-down: #@ Stop all the database (Docker)
	$(DB_COMMAND) down
db-keyfile-create: #@ Generate the keyfile for the database.
	$(DB_MAKE_KEY)
db-keyfile-remove: #@ Remove the keyfile for the database.
	$(DB_REMOVE_KEY)
db-backup: #@ Backup MongoDB database using Docker (Compressed). Usage: make db-backup [BACKUP_DIR=/absolute/path/to/dir]
	@case "$(BACKUP_DIR)" in /*) ;; *) echo "Error: BACKUP_DIR must be an absolute path. Got: $(BACKUP_DIR)"; exit 1;; esac
	mkdir -p $(BACKUP_DIR)
	echo "Use the command 'docker log --tail 100 -f mongo-backup' to see the progress"
	docker rm -f mongo-backup 2>/dev/null || true
	docker run --name mongo-backup -d --network=host -v $(BACKUP_DIR):/backup -u $(shell id -u):$(shell id -g) mongo:8 mongodump --uri="$(MONGO_URL)" --gzip --archive="/backup/veworld-db-$$(date +%Y%m%d%H%M%S).gz"
db-restore: #@ Restore MongoDB database from a backup file. Usage: make db-restore FILE=/absolute/path/to/backup.gz
	@if [ -z "$(FILE)" ]; then \
		echo "Error: FILE is required. Usage: make db-restore FILE=/absolute/path/to/backup.gz"; \
		exit 1; \
	fi
	@case "$(FILE)" in /*) ;; *) echo "Error: FILE must be an absolute path. Got: $(FILE)"; exit 1;; esac
	@if [ ! -f "$(FILE)" ] || [ ! -r "$(FILE)" ]; then \
		echo "Error: FILE must exist and be a readable file. Got: $(FILE)"; \
		exit 1; \
	fi
	echo "Use the command 'docker log --tail 100 -f mongo-restore' to see the progress"
	docker rm -f mongo-restore 2>/dev/null || true
	docker run --name mongo-restore -d --network=host -v $(FILE):/backup/backup.gz -u $(shell id -u):$(shell id -g) mongo:8 mongorestore --uri="$(MONGO_URL)" --drop --gzip --archive="/backup/backup.gz" --numInsertionWorkersPerCollection 16
