# VeWorld Indexer

[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=vechainfoundation_veworld-indexer&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)

![Gradle Tests](https://github.com/vechainfoundation/veworld-indexer/actions/workflows/gradle-test.yaml/badge.svg?branch=main)
![Docker Build](https://github.com/vechainfoundation/veworld-indexer/actions/workflows/docker-build.yml/badge.svg?branch=main)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=bugs&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=vulnerabilities&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=duplicated_lines_density&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=reliability_rating&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=sqale_index&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=ncloc&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=code_smells&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=sqale_rating&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=vechainfoundation_veworld-indexer&metric=security_rating&token=e9da123057ea6bd22033e6388a64cc25d2a50f81)](https://sonarcloud.io/summary/new_code?id=vechainfoundation_veworld-indexer)

- Coverage Reports:
    - [API](https://ideal-fortnight-7vp33mg.pages.github.io/api/coverage/)
    - [Common](https://ideal-fortnight-7vp33mg.pages.github.io/common/coverage/)
    - [Indexer](https://ideal-fortnight-7vp33mg.pages.github.io/indexer/coverage/)

- Tests Results:
    - [API](https://ideal-fortnight-7vp33mg.pages.github.io/api/tests/)
    - [Common](https://ideal-fortnight-7vp33mg.pages.github.io/common/tests/)
    - [Indexer](https://ideal-fortnight-7vp33mg.pages.github.io/indexer/tests/)
    - [E2E](https://ideal-fortnight-7vp33mg.pages.github.io/e2e/tests/)

## Prerequisites

- Docker
- Java (v17)

## Getting Started

- To see a list of all available commands, run `make help`
- After starting the application, the swagger will be made available at `http://localhost:8080`

### Option 1: Docker only

- Copy env files for the two packages `./package/<package>/.env.example` to `./package/<package>/.env` and fill in the
  values for your environment. They should work as-is for docker

- Run:

```
make start
```

### Option 2: Docker + IntelliJ (Recommended for debugging)

- No need to copy the environment files if you are using IntelliJ. The default variables should connect to the
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

## Features

There are 6 indexers and 6 corresponding APIs. Each indexer can be run in isolation or all together. There is no
dependency between indexers for this reason. Each indexer and API pair can be enabled using the corresponding spring
profile.

- `blocks` - enabled with `blocks` profile or `blocks-proxy` to proxy to the Thor node
- `transactions` - enabled with `transactions` profile
- `clauses` - enabled with `clauses` profile
- `contracts` - enabled with `contracts` profile
- `nft-events` - enabled with `nft-events` profile
- `transfer-events` - enabled with `transfer-events` profile

As you can see from the list above, the block indexer offers the option to proxy to the Thor node. This is useful if you
want the convenience of the Block endpoints without the overhead of indexing the data.

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

