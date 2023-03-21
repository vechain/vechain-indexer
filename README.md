# VeWorld Indexer

## Prerequisites

- Docker
- Java  (v17)

## Getting Started

- To see a list of all available commands, run `make help`


### Option 1: Docker only

- Run:
```
make start
```

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