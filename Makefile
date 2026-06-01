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

# Dependency Locking
update-locks: #@ Refresh Gradle lockfiles (root + all subprojects) after a dependency change.
	./gradlew resolveAndLockAll --write-locks

# Application Build (Gradle)
build: format build-indexer build-api #@ Build the application with Gradle.
	echo "Build completed."
.PHONY:build
build-indexer: build-thor-scheduler #@ Build the application with Gradle.
	./gradlew :package:indexer:build -x test
build-thor-scheduler: #@ Build the Go thor-scheduler co-process used by the ValidatorV2 indexer.
	$(MAKE) -C tools/thor-scheduler build
build-api: #@ Build the application with Gradle.
	./gradlew :package:api:build -x test

# Application Build (Docker)
comma := ,
GRADLE_PROPERTIES_FILE ?= gradle/docker.gradle.properties
export GRADLE_PROPERTIES_FILE
GRADLE_SECRET := $(if $(GRADLE_PROPERTIES_FILE),--secret id=gradle_props$(comma)src=$(GRADLE_PROPERTIES_FILE),)
build-image: build-image-indexer build-image-api #@ Build the application with Docker.
	echo "Build completed."
build-image-indexer: ensure-gradle-props #@ Build the application with Docker.
	docker build $(GRADLE_SECRET) --build-arg APP_VERSION=v.1.0.0 --build-arg PACKAGE_NAME=indexer -t veworld-indexer .
build-image-api: ensure-gradle-props #@ Build the application with Docker.
	docker build $(GRADLE_SECRET) --build-arg APP_VERSION=v.1.0.0 --build-arg PACKAGE_NAME=api -t veworld-api .

test: #@ Run all the tests (excluding e2e).
	./gradlew cleanTest test -x :packages:e2e:test
test-ci: #@ Run all tests for CI (with caching, excludes e2e).
	./gradlew test -x :packages:e2e:test
test-e2e: ensure-gradle-props #@ Run all the end-to-end tests.
	./gradlew :packages:e2e:test --stacktrace
test-api: #@ Run all the API tests.
	./gradlew clean :package:api:test
test-indexer: #@ Run all the indexer tests.
	./gradlew :package:indexer:test
test-common: #@ Run all the common tests.
	./gradlew clean :package:common:test

# API Schema Tests (Schemathesis via Docker)
SCHEMA_TEST_BASE_URL ?= http://host.docker.internal:8080
SCHEMA_TEST_MAX_EXAMPLES ?= 200
SCHEMA_TEST_MAX_RESPONSE_TIME_SECONDS ?= 2
SCHEMA_TEST_WORKERS ?= 4
RATE_LIMIT_BYPASS_TOKEN ?=
RATE_LIMIT_BYPASS_HEADER ?= x-rate-limit-bypass

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
		--max-response-time=$(SCHEMA_TEST_MAX_RESPONSE_TIME_SECONDS) \
		--workers=$(SCHEMA_TEST_WORKERS) \
		$(if $(RATE_LIMIT_BYPASS_TOKEN),-H "$(RATE_LIMIT_BYPASS_HEADER): $(RATE_LIMIT_BYPASS_TOKEN)")

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
	make app-down db-clean
down: #@ Stop all the infrastructure and the application.
	make app-down db-down

# Application
ensure-gradle-props:
	@if [ "$(GRADLE_PROPERTIES_FILE)" = "gradle/docker.gradle.properties" ] && [ ! -f "$(GRADLE_PROPERTIES_FILE)" ]; then \
		cp gradle/docker.gradle.properties.example "$(GRADLE_PROPERTIES_FILE)"; \
	fi

app-up: format ensure-gradle-props #@ Start the application.
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
dd-get-app-pipeline: #@ Fetch Datadog app pipeline config.
	$(DD_SCRIPT) get-app-pipeline
dd-get-waf-pipeline: #@ Fetch Datadog WAF pipeline config.
	$(DD_SCRIPT) get-waf-pipeline
dd-get-dashboard: #@ Fetch Datadog dashboard config.
	$(DD_SCRIPT) get-dashboard
dd-generate-openapi: #@ Generate OpenAPI spec from API with embedded MongoDB.
	./gradlew :packages:api:generateOpenApiSpec
dd-refresh-generated: dd-generate-openapi dd-update-categories format-json #@ Refresh committed OpenAPI and Datadog generated JSON files for both main and WAF pipelines.
dd-push-pipeline: #@ Push pipeline config to Datadog.
	$(DD_SCRIPT) push-pipeline
dd-push-app-pipeline: #@ Push Datadog app pipeline config.
	$(DD_SCRIPT) push-app-pipeline
dd-push-waf-pipeline: #@ Push Datadog WAF pipeline config.
	$(DD_SCRIPT) push-waf-pipeline
dd-push-dashboard: #@ Push dashboard config to Datadog.
	$(DD_SCRIPT) push-dashboard
dd-update-categories: #@ Update main and WAF pipeline categories from api-docs.json.
	$(DD_SCRIPT) update-categories
dd-validate-categories: #@ Validate main and WAF pipeline categories match api-docs.json.
	$(DD_SCRIPT) validate-categories
dd-sync: dd-get-pipeline dd-get-app-pipeline dd-get-waf-pipeline dd-get-dashboard #@ Fetch Datadog pipelines and dashboard.
dd-push: dd-push-pipeline dd-push-app-pipeline dd-push-waf-pipeline dd-push-dashboard #@ Push Datadog pipelines and dashboard.

# Token Registry
refresh-token-registry: #@ Refresh bundled token registry files from vechain.github.io.
	bash packages/api/scripts/refresh_token_registry.sh

# Database
DB_COMMAND=docker compose -f database/docker-compose-mongo.yaml
DB_MAKE_KEY=mkdir -p database/keys && [ -f database/keys/keyfile ] || openssl rand -base64 756 > database/keys/keyfile
DB_REMOVE_KEY=rm -f -R database/keys
DB_SETUP_COMMAND=docker compose -p database-setup -f database/docker-compose-mongo-setup.yaml
MONGO_URL=mongodb://indexer:password@localhost:27017/vechain?authSource=admin
BACKUP_DIR ?= $(PWD)/database/backups

db-all: #@ Remove, clean and start the database (Docker).
	make db-clean db-up db-setup
db-up: db-keyfile-create #@ Start all the database (Docker)
	$(DB_COMMAND) up -d --wait
db-setup: db-up #@ Setup all the database (Docker)
	@status=0; \
	$(DB_SETUP_COMMAND) up --build --abort-on-container-exit --exit-code-from mongo-setup || status=$$?; \
	if [ $$status -ne 0 ]; then \
		echo "Mongo setup failed. Database container status:" >&2; \
		$(DB_COMMAND) ps || true; \
		echo "Recent mongo-node1 logs:" >&2; \
		$(DB_COMMAND) logs --tail=200 mongo-node1 || true; \
	fi; \
	$(DB_SETUP_COMMAND) rm --force; \
	exit $$status
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
	mkdir -p "$(BACKUP_DIR)"
	echo "Use the command 'docker log --tail 100 -f mongo-backup' to see the progress"
	docker rm -f mongo-backup 2>/dev/null || true
	docker run --name mongo-backup -d --network=host -v "$(BACKUP_DIR):/backup" -u $(shell id -u):$(shell id -g) mongo:8 mongodump --uri="$(MONGO_URL)" --gzip --archive="/backup/veworld-db-$$(date +%Y%m%d%H%M%S).gz"
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
	docker run --name mongo-restore -d --network=host -v "$(FILE):/backup/backup.gz" -u $(shell id -u):$(shell id -g) mongo:8 mongorestore --uri="$(MONGO_URL)" --drop --gzip --archive="/backup/backup.gz" --numInsertionWorkersPerCollection 16
db-copy-collections: #@ Copy specific MongoDB collections between two clusters. Interactive. See database/restore/README.md.
	database/restore/restore.sh
