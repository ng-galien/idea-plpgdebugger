# Intellij PL/pg SQL debugger

![Build](https://github.com/ng-galien/idea-plpgdebugger/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/18419-postgresql-debugger.svg)](https://plugins.jetbrains.com/plugin/18419-postgresql-debugger)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18419-postgresql-debugger.svg)](https://plugins.jetbrains.com/plugin/18419-postgresql-debugger)

<!-- Plugin description -->
Debug PL/pg stored procedures, functions, triggers and views from Intellij IDEs (Ultimate only)

## Features

- Debug queries from editor by selecting a [function call](#debug-a-routine-from-the-editor)
- Debug routines and triggers from [database explorer](#debug-a-routine-from-the-database-explorer)
- Full support for variables inspection with [Docker custom debugger](https://github.com/ng-galien/idea-plpgdebugger/blob/221/docker/README.md)

Visit the plugin [page](https://plugins.jetbrains.com/plugin/18419-postgresql-debugger) at JetBrains.  
Report a bug or a problem => [Create an issue](https://github.com/ng-galien/idea-plpgdebugger/issues/new/choose)
<!-- Plugin description end -->

## Getting started

### Install the debugger on the server

You can use the plugin with the standard pldbgapi extension, but you will not be able to inspect every variable type.  
To get the full experience, you can use an [enhanced version](https://github.com/ng-galien/pldebugger) with the plugin.  

You can compile the extension from the source code or use one of the [Docker images](https://hub.docker.com/repository/docker/galien0xffffff/postgres-debugger/general) available.

> The Docker image is based on the official PostgreSQL image and includes the debugger extension, from version 11 to 16 for amd64 and arm64.
> To build your own image, you can use the [Dockerfile](https://github.com/ng-galien/pldebugger/tree/print-vars/docker) provided.

```shell
docker run -p 5515:5432 --name PG15-debug -e POSTGRES_PASSWORD=postgres -d galien0xffffff/postgres-debugger:15
```

Or install the [debugger](https://www.pgadmin.org/docs/pgadmin4/development/debugger.html) binaries on  your machine.

## Server Configuration

To ensure the debugger shared libraries are correctly configured on your PostgreSQL server, follow these steps:

1. **Check if the shared library is loaded**:
   Run the following SQL command to verify if the `pldbgapi` extension is loaded:

   ```sql
   SELECT * FROM pg_extension WHERE extname = 'pldbgapi';
   ```

   If the extension is not listed, you need to install it.

2. **Verify the shared library path**:
   Ensure that the `shared_preload_libraries` parameter in your `postgresql.conf` file includes `pldbgapi`. You can check this by running:

   ```sql
   SHOW shared_preload_libraries;
   ```

   If `pldbgapi` is not included, add it to the `postgresql.conf` file:

   ```conf
   shared_preload_libraries = 'pldbgapi'
   ```

   After making changes, restart the PostgreSQL server.

3. **Check the installation of the debugger extension**:
   Run the following command to ensure the debugger extension is installed in the correct schema:

   ```sql
    SELECT * FROM pg_extension;
   ```

   Look for `pldbgapi` in the list of installed extensions. If it is not present, install it using:

   ```sql
   CREATE EXTENSION IF NOT EXISTS pldbgapi;
   ```

By following these steps, you can confirm that the debugger shared libraries are properly configured on your PostgreSQL server.

### Activate the debugger on the database

Run the following command on the database where you want to debug routines

```sql
--Take care to install the extension on the public schema
CREATE EXTENSION IF NOT EXISTS pldbgapi;
```



### Debug a routine from the editor

Just write a statement using the function you want to debug

```sql
--This is a statement in a console or in a file
SELECT function_name(args);
```
(click on the debug icon on the top left of the editor)

![](img/direct.gif)

### Debug a routine from the database explorer

![](img/indirect.gif)

(Right-click on the routine you want to debug and select "Debug Routine")

### Variable inspection

In the variables tab you can inspect:
- Primitive types
- Arrays
- JSON

With the docker image you can also inspect:
- Composite types
- Record types

![](img/inspect-variables.png)

### Inline values

Arguments and variables are displayed in the code editor  

![](img/inline-variables.png)

### Debug process

The debug session is displayed as a background process. You can stop it by clicking on the stop icon. 

When you stop the debug session, the process is killed on the server side.  

If you debug a routine from the code editor, the process is automatically killed when you close the editor.  

If you debug a routine from the database explorer, the process remains active until you stop manually.

![](img/background-process.png)

## Limitation of the standard pldbgapi

The standard pldbgapi does not send back composite variable, but you can put it in arrays to inspect them.

## Installation

### Debugger binaries

You must first install the debugger extension and activate the shared library onto the server.  

```shell
EXPORT TAG = 11 # or 11, 12, 13, 14, 15
EXPORT PG_LIB=postgresql-server-dev-${TAG}
EXPORT PG_BRANCH=REL_${TAG}_STABLE
EXPORT PLUGIN_BRANCH=print-vars

# Install dependencies
apt --yes update && apt --yes upgrade && apt --yes install git build-essential libreadline-dev zlib1g-dev bison libkrb5-dev flex $PG_LIB \
#
cd /usr/src/
# Install postgres source
git clone -b $PG_BRANCH --single-branch https://github.com/postgres/postgres.git
# Setup postgres
cd postgres && ./configure
# Install debugger extension
cd /usr/src/postgres/contrib
git clone -b $PLUGIN_BRANCH --single-branch https://github.com/ng-galien/pldebugger.git
cd pldebugger
# Compile with the same options as postgres
make clean && make USE_PGXS=1 && make USE_PGXS=1 install
```

Follow these [instructions for PgAdmin](https://www.pgadmin.org/docs/pgadmin4/development/debugger.html) for a standard installation.


### Intellij IDE

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "idea-plpgdebugger"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/ng-galien/idea-plpgdebugger/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
