# VeWorld Indexer

## Prerequisites

- Docker
- Java  (v17)

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