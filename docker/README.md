# Docker Image

## Build

> export PG_VERSION=14 
> docker build --build-arg "TAG=$PG_VERSION" -t "galien0xffffff/postgres-debugger:$PG_VERSION" .

## Run

> docker run -p 5514:5432 --name PG14-debug -e POSTGRES_PASSWORD=postgres -d galien0xffffff/postgres-debugger:14

## Functions to duplicate form pl_exec.c

* exec_eval_datum
* convert_value_to_string
* plpgsql_fulfill_promise
* get_stmt_mcontext
* instantiate_empty_record_variable
* revalidate_rectypeid
* make_tuple_from_row
* assign_text_var
* assign_simple_var