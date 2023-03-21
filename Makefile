help:
	@egrep -h '\s#@\s' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?#@ "}; {printf "\033[36m  %-30s\033[0m %s\n", $$1, $$2}'

# All
start: #@ Remove, clean and start all the infrastructure and the application.
	make infra-up app-up
clean: #@ Clean all the infrastructure and the application data.
	make infra-clean
down: #@ Stop all the infrastructure and the application.
	make infra-down app-down

# Application
app-up: #@ Start the application.
	docker compose up -d --build
app-down: #@ Stop the application.
	docker compose down

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
DB_SETUP_COMMAND=docker compose -f database/docker-compose-mongo-setup.yaml\

db-all: #@ Remove, clean and start all the database.
	make db-down db-clean db-up db-setup
db-clean: #@ Clean all the database data
	$(DB_COMMAND) down -v --remove-orphans
db-down: #@ Stop all the database.
	$(DB_COMMAND) down
db-up: #@ Start all the database.
	$(DB_COMMAND) up -d
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
	$(THOR_COMMAND) up -d --wait

# Application Build
build-gradle: #@ Build the application with Gradle.
	./gradlew build
build-docker: #@ Build the application with Docker.
	docker build -t veworld-indexer .
