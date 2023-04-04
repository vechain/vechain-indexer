# VeWorld Indexer

## Prerequisites

- Docker
- Java (v17)

## Getting Started

- To see a list of all available commands, run `make help`
- After starting the application, the swagger will be made available at `http://localhost:8080`
- There is a postman collection in the directory `./postman`

### Option 1: Docker only

- Copy `.env.example` to `.env.local` and fill in the values for your environment. They should work as-is for docker

- Run:

```
make start
```

### Option 2: Docker + IntelliJ (Recommended for debugging)

- No need to copy `.env.example` to `.env` if you are using IntelliJ. The default variables should connect to the
  infrastructure using localhost variables
- Run:

```bash
make infra-all
```

- Go to IndexerApplication.kt inside IntelliJ and run/debug:

![img.png](images/intellij-start.png)

### Restarting

- Clean and restart all infrastructure:

```bash
make infra-all
```

- Clean and restart the DB:

```bash
make db-all
```

- Clean and restart Thor:

```bash
make thor-all
```

## Testing

- Run all the tests:

```bash
make test
```

### Package Testing

- There are 4 packages that can be tested (`api`, `common`, `e2e`, `indexer`)
- This will run all tests in a package (unit, integration and E2E)
- Run (example for `api`):

```bash
make test-api
```

### E2E

- Running E2E tests will spin up all the docker infrastructure before the test and tears it down after completion.
  This is useful for testing the entire system, but it is slow. If you need to debug these tests, it is recommended to
  spin up
  the network manually using `make clean start` and remove the tasks `preE2e` and `postE2e` tasks
  in `./packages/e2e/build.gradle.kts` ([here](./packages/e2e/build.gradle.kts))

### Load Testing

- For a successful run, ensure `./docker-compose.yml` is running
- To run the load tests:

```bash
make load-test
```
