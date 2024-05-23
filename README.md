# VeWorld Indexer

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
- Java (v17)

## Getting Started

- To see a list of all available commands, run `make help`
- After starting the application, the swagger will be made available at `http://localhost:8080`

MongoDB requires a keyfile in other to run. To create this keyfile, run the following command:

```bash
make db-keyfile-create
```

You will only ever need to run this command once, unless you delete it. To remove the keyfile you can run the following
command:

```bash
make db-keyfile-remove
```

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

![img.png](images/intellij-start.png)

### Connecting to MongoDB

Connect for the various users with the following URIs:

- `indexer` - `mongodb://indexer:password@localhost:27017/vechain?directConnection=true&authMechanism=DEFAULT&authSource=admin`
- `api` - `mongodb://api:password@localhost:27017/vechain?directConnection=true&authMechanism=DEFAULT&authSource=admin`
- `root` - `mongodb://root:password@localhost:27017/admin?directConnection=true&authMechanism=DEFAULT`
- Go to IndexerApplication.kt inside IntelliJ and run/debug:

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

## Database migration

The indexer package `org.vechain.indexer.migration` contains the change units responsible for creating and/or migrating the database schema and data.
It's powered by `Mongock` (Cf. https://docs.mongock.io/).

It's intended to provide a code-first approach for db migrations, enabling code and database changes to be shipped
together and be kept in sync at all times.

### How it works
- Mongock is autoconfigured via its spring boot starter, and it applies db changes through the spring data mongodb driver.
- To apply a new migration, simply add a new `@ChangeUnit` class in a sub-package named with the version of the code it's applied to.
- Mongock maintains a changelog collections to keep track of applied migrations, and loads new ones.
- The mongock runner obtains a pessimistic lock for the duration of the change unit migration, so it's safe to be used in a distributed env.
- The migration is applied on the spring app startup; in case of migration failure, the application simply will not complete startup.

### Change units guidelines
- For new code versions, change units must be placed in a new sub-package of the migration package with the same version.
- Change units should ideally be idempotent, allowing for them to be interrupted and resumed at any time. This means checking for collection existence, and ensuring idx existence, instead of just creating them.
- Change units should be kept backwards compatible as much as possible.
- Change units should be kept light & fast, so as not to present a risk to application's startup. New indexes in heavy collections in particular should be created in the background and carefully monitored.

### Domains and URLs
The domains and URLs for the VeWorld Indexer project are the following:

#### Public URLs
- Live Mainnet Production: https://indexer.mainnet.vechain.org
- Live Testnet Production: https://indexer.testnet.vechain.org

#### Internal URLs
The domains above are held by CNAME records which forward traffic to the following URLs. Besides internal testing purposes, these are used to delgate the concern of route switching between environments away from the vecahin-domains repository/AWS account. These CNAME records in turn point to the DNS of the current live environment's application load balancers:

- Live Mainnet Production: https://mainnet.live.prod.veworld.vechain.org
- Live Testnet Production: https://testnet.live.prod.veworld.vechain.org

When it exists, the non-live (or dead) environment can be accessed via the following domains. These are to be used internally for testing purposes:

- Dead Mainnet Production: https://mainnet.dead.prod.veworld.vechain.org
- Dead Testnet Production: https://testnet.dead.prod.veworld.vechain.org


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


### Terraform
The infrastructure for the VeWorld Indexer is managed by two terrafrom projects, `terraform/api` and `terraform/vpc`. Any infrastructure which needs to exist permanently (for example the hosted zone and DNS records for the internal domains) are defined in `vpc`, while any environment-specific infrastructure, such as ECS services, load balancers, IAM roles etc are defined in `api`.