# VeWorld Indexer

![Scorecard badge](https://github.com/vechain/veworld-indexer/blob/feature/scorecard-action/.assets/scorecard-badge.svg)
![Security Checks Badge](https://github.com/vechain/veworld-indexer/actions/workflows/security-checks.yml/badge.svg)

[![Test, Publish & Deploy](https://github.com/vechain/veworld-indexer/actions/workflows/on-main.yml/badge.svg)](https://github.com/vechainfoundation/veworld-indexer/actions/workflows/on-main.yml)

[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=vechain_veworld-indexer&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=bugs&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=vulnerabilities&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=duplicated_lines_density&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=reliability_rating&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=sqale_index&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=ncloc&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=code_smells&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=sqale_rating&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=vechain_veworld-indexer&metric=security_rating&token=0582f95ddc9a13d328efea4a99db7eb3fa95ebaf)](https://sonarcloud.io/summary/new_code?id=vechain_veworld-indexer)

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
- Java (v21)

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
make db-all
```

![img.png](images/intellij-start.png)

### Connecting to MongoDB

Connect for the various users with the following URIs:

- `indexer` - `mongodb://indexer:password@localhost:27017/vechain?directConnection=true&authMechanism=DEFAULT&authSource=admin`
- `api` - `mongodb://api:password@localhost:27017/vechain?directConnection=true&authMechanism=DEFAULT&authSource=admin`
- `root` - `mongodb://root:password@localhost:27017/admin?directConnection=true&authMechanism=DEFAULT`
- Go to IndexerApplication.kt inside IntelliJ and run/debug:

### Restarting

- Clean and restart the DB:

```bash
make db-all
```

## Backup MongoDB
You can back up the database by running the following command:

```bash
make db-backup
```
- Will back up the vechain database from localhost:27017
- The backup will be stored in the database/backups/ directory.
- The filename follows this format: database/backups/vechain-YYYYMMDDHHMMSS

You can also specify the host and port of the MongoDB instance you want to backup:

```bash
make db-backup MONGO_HOST=my-mongo-host:32423
```

## Restore MongoDB from backup

You can restore the database by running the following command:

```bash
make db-restore
```
- If no backup exists, you will be prompted to specify a backup directory.
- By default, it restores from the latest backup found in the database/backups/ directory.

To restore from a specific backup folder, specify DIR:
```bash
make db-restore DIR=backup/mydatabase-20250210
```

To restore to a different DB:
```bash
make db-restore MONGO_HOST=myserver.com
```

## Features

There are 6 indexers and 6 corresponding APIs. Each indexer can be run in isolation or all together. There is no
dependency between indexers for this reason. Each indexer and API pair can be enabled using the corresponding spring
profile.

- `transactions` - enabled with `transactions` profile
- `nfts` - enabled with `nfts` profile
- `transfers` - enabled with `transfers` profile
- `history` - enabled with `history` profile

As you can see from the list above, the block indexer offers the option to proxy to the Thor node. This is useful if you
want the convenience of the Block endpoints without the overhead of indexing the data.

## Pruner
Some of the indexers are stateful indexers. This means that records are updated with each block. In order to facilitate rollbacks we must store all previous version of each record. These records are stored in collections with a `-archives` postfix. As you might imagine these archive collections can get rather large over time. To prevent the collection from blowing up we have implemented an optional pruner service that can be enabled and configured with the following env variablers.

- `PRUNER_ENABLED` - A boolean to enable or disable the pruner
- `PRUNER_INTERVAL` - How frequently to run the pruner (in milliseconds)
- `PRUNER_INITIAL_DELAY` - An initial delay after startup before running for the first time (in milliseconds)
- `PRUNER_REMOVAL_CHUNK_SIZE` - Sometimes the number of records to prune can be very large. To prevent mongoDB from blowing up we can set a chunk size for the delete operation

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
make test-common
make test-indexer
make test-e2e
```

### E2E

- Running E2E tests will spin up all the docker infrastructure before the test and tears it down after completion.
  This is useful for testing the entire system, but it is slow. If you need to debug these tests, it is recommended to
  spin up
  the network manually using `make clean start` and remove the tasks `preE2e` and `postE2e` tasks
  in `./packages/e2e/build.gradle.kts` ([here](./packages/e2e/build.gradle.kts))

### Load Testing

Update the `BASE_URL` variable in `load-testing/docker-compose.yml` to point to your target environment. Individual tests can
be tailored by modifying the env variables for each service.
 - `RAMP_UP_DURATION` - The time taken to ramp up to the maximum number of users
 - `STAY_DURATION` - The time to stay at the maximum number of users
 - `WIND_DOWN_DURATION` - The time taken to ramp down to 0 users
 - `TARGET_VUS` - The target number of virtual users to simulate

Run the load tests:

```bash
make load-test
```

The containers will not be destroyed after the test completes. This is to allow access to the grafana dashboard and container logs. To clean up the load testing environment:

```bash
make load-test-clean
```

You can also choose to run specific tests only. We have included some commands in the Makefile to make this more convenient. For example, to run the history load tests:

```bash
make load-test-history
```

## Deployment & Testing

The VeWorld Indexer can be deployed via two strategies: Regular or Blue/Green. To trigger a deployment, run the [Prod Deployment Workflow](https://github.com/vechain/veworld-indexer/actions/workflows/deploy-prod.yml). You will be prompted to select the deployment strategy and the version number. Please enter a version in the format `major.minor.patch` - this will be used to create a new release & tag. If in doubt about which environment is currently live, run the [Identify Live/Dead Environments](https://github.com/vechain/veworld-indexer/actions/workflows/identify-live-color.yml) workflow with the default arguments.

### Regular Deployment
Selecting the `regular` deployment strategy will trigger a deployment to the current live production environment. Most deployments will follow this process. Ensure any changes being deployed via this strategy have been sufficiently tested before triggering (testing process described below).

### Blue/Green Deployment
For code changes requiring a full reindex from the genesis block, deploy via the `blue/green` strategy. This will trigger a deployment to the dead color. Following deployment, a sufficient amount of time will need to be left (ie a few days) until the database has caught up with the latest block. After this point, the live environment can be switched from the current live color to the dead color. To do this, run the [Switch Live Environment](https://github.com/vechain/veworld-indexer/actions/workflows/switch-live-dns.yml) workflow. This will update the appropriate DNS records to redirect traffic to the alternate color environment. 

Following the DNS switch, please wait at least 48 hours before tearing down the old live (now dead) environment. This is to allow any remote DNS caches to update to the new live environment.

### Testing
Since blue/green deployments are fairly infrequent, the dead color can be used as a transient testing environment when needed. This is preferable to using the dev environment because the dead color is an exact replica of the live environment, whereas dev is a more lightweight, stripped-down version, missing some key components like a mongo atlas cluster. Deployment to the dead color will be performed automatically on merge to main, *as long as the dead environment already exists*. The environment will therefore need to be deployed manually (either by deploying the terraform locally or by triggering a [Prod Deployment](https://github.com/vechain/veworld-indexer/actions/workflows/deploy-prod.yml) of the dead color).

### Environment Tear-down
When testing is complete, or when a DNS switch has migrated traffic from one environment to the other, the dead environment can be safely torn down until needed again. To do this, run the [Cluster Destroy](https://github.com/vechain/veworld-indexer/actions/workflows/destroy-environment.yml) workflow, and select the appropriate environment when prompted.

Further details on the CICD process can be found [here](https://vechainfoundation-my.sharepoint.com/:w:/g/personal/dougal_rea_vechain_org/EcuW_jUEDh5PhS8jzH5PJ0EBw9pY8EqSd5IWKCJIG8rGVQ?e=i0Pa0N)
