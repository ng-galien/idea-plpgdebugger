# Docker Image

## Build the image

```bash
export PG_VERSION=14 \
&& export PG_IMAGE=postgres-with-debugger \
&& docker build --build-arg "TAG=$PG_VERSION" -t "$PG_IMAGE:$PG_VERSION" .
```

## Run the image

```bash
export PG_VERSION=14 \
&& export PG_IMAGE=postgres-with-debugger \
&& docker run -p 55$PG_VERSION:5432 --name "PostgresSQL-$PG_VERSION-debug" -e POSTGRES_PASSWORD=postgres -d "$PG_IMAGE:$PG_VERSION"
```
