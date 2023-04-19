help:
	@egrep -h '\s#@\s' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?#@ "}; {printf "\033[36m  %-30s\033[0m %s\n", $$1, $$2}'

test: #@ Run all the tests.
	./gradlew cleanTest test
test-e2e: #@ Run all the end-to-end tests.
	./gradlew clean :package:e2e:test
test-api: #@ Run all the API tests.
	./gradlew clean :package:api:test
test-indexer: #@ Run all the indexer tests.
	./gradlew clean :package:indexer:test
test-common: #@ Run all the common tests.
	./gradlew clean :package:common:test

# Load Testing
LOAD_TEST_COMMAND=docker compose -f load-testing/docker-compose.yaml
load-test: #@ Run the load tests.
	$(LOAD_TEST_COMMAND) up --build -d --wait; open http://localhost:3000/d/GlqvWKLVk/k6-load-testing-results\?orgId\=1\&refresh\=5s\&from\=now-5m\&to\=now
load-test-clean: #@ Clean the load tests data.
	$(LOAD_TEST_COMMAND) down -v --remove-orphans

# Application Build
build-gradle: #@ Build the applications with Gradle.
	./gradlew packages:api:build packages:indexer:build -x test
build-indexer: #@ Build the application with Docker.
	docker build --build-arg VEWORLD_PACKAGE=indexer -t veworld-indexer .
build-api: #@ Build the application with Docker.
	docker build --build-arg VEWORLD_PACKAGE=api -t veworld-api .
build-k6: #@ Build the K6 docker image.
	docker build -t veworld-k6 load-testing

# All
start: #@ Remove, clean and start all the infrastructure and the application.
	make infra-up infra-setup app-up
clean: #@ Clean all the infrastructure and the application data.
	make load-test-clean app-down infra-clean load-test-clean
down: #@ Stop all the infrastructure and the application.
	make app-down infra-down

# Application
app-up: #@ Start the application.
	docker compose up -d --build --wait
app-down: #@ Stop the application.
	docker compose down
app-logs: #@ Attach to the application logs.
	docker compose logs -f

# Infra
infra-all: #@ Remove, clean and start all the infrastructure.
	make infra-down infra-clean infra-up infra-setup
infra-clean: #@ Clean all the infrastructure data
	make db-clean thor-clean
infra-down: #@ Stop all the infrastructure.
	make db-down thor-down
infra-setup: #@ Setup all the infrastructure.
	make db-setup
infra-up: #@ Start all the infrastructure.
	make db-up & make thor-up

# Database
DB_COMMAND=docker compose -f database/docker-compose-mongo.yaml
DB_SETUP_COMMAND=docker compose -f database/docker-compose-mongo-setup.yaml

db-all: #@ Remove, clean and start all the database.
	make db-down db-clean db-up db-setup
db-clean: #@ Clean all the database data
	$(DB_COMMAND) down -v --remove-orphans
db-down: #@ Stop all the database.
	$(DB_COMMAND) down
db-up: #@ Start all the database.
	$(DB_COMMAND) up -d --wait
db-setup: #@ Setup all the database.
	$(DB_SETUP_COMMAND) up; $(DB_SETUP_COMMAND) rm --force

# Thor
THOR_COMMAND=docker compose -f thor/docker-compose.yaml

thor-all: #@ Remove, clean and start VeChainThor.
	make thor-down thor-clean thor-up
thor-clean: #@ Clean the VeChainThor data
	$(THOR_COMMAND) down -v --remove-orphans
thor-down: #@ Stop VeChainThor
	$(THOR_COMMAND) down
thor-up: #@ Start VeChainThor
	$(THOR_COMMAND) up -d --wait --build
thor-test: #@ Test VeChainThor
	$(THOR_COMMAND) up thor-tx-script
