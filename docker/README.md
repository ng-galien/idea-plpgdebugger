# Docker Image

## Build

> export PG_VERSION=14 && docker build --build-arg "TAG=$PG_VERSION" -t "ng-galien/postgres-debugger:$PG_VERSION" .

## Run

> docker run -p 5514:5432 --name PG14-debug -e POSTGRES_PASSWORD=postgres -d galien0xffffff/postgres-debugger:14