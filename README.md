# Intellij PL/pg SQL debugger

![Build](https://github.com/ng-galien/idea-plpgdebugger/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/18419-postgresql-debugger.svg)](https://plugins.jetbrains.com/plugin/18419-postgresql-debugger)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18419-postgresql-debugger.svg)](https://plugins.jetbrains.com/plugin/18419-postgresql-debugger)

<!-- Plugin description -->
Debug PL/pg SQL with Intellij Database Tools

## Features

- Debug queries from editor
- Debug routines and triggers from database explorer
- Full support for variables inspection with [Docker custom debugger](https://github.com/ng-galien/idea-plpgdebugger/blob/221/docker/README.md)

Submit features => [Production ready task list](https://github.com/ng-galien/idea-plpgdebugger/issues/17)  
Report a bug or a problem => [Create an issue](https://github.com/ng-galien/idea-plpgdebugger/issues/new/choose)
<!-- Plugin description end -->

### Use the enhanced debugger in a Docker container

Try out the Docker image with the ready to use enhanced debugger (versions 11 to 14).

> docker run -p 5514:5432 --name PG14-debug -e POSTGRES_PASSWORD=postgres -d galien0xffffff/postgres-debugger:14

Run this script on your database

> CREATE EXTENSION if not exists pldbgapi;

To build your own image instructions are [here](docker/README.md)

### Debug a routine from the editor

Just write a statement using the function you want to debug

> SELECT function_name(args)

![](img/direct.gif)

### Debug a routine from the database explorer

Choose Debug routine action in the database tree and run it in a console

![](img/indirect.gif)

### Variable inspection

(Composite types are not returned by the extension see [Limitation](#limitation))

- Basic variables
- Arrays(including array of composite)

![](img/inspect-variables.png)

### Inline values

Arguments and variables are displayed in code, it can be disabled in the plugin configuration.  

![](img/inline-variables.png)

### Debug process

The debugger activity is visible as a background process in the IDE

![](img/background-process.png)

## Limitation

The standard pldbgapi does not send back composite variable, but you can put it in arrays to inspect them.  
A modified extension is available in this [repo](https://github.com/ng-galien/pldebugger), hope this can be integrated in the official extension soon.  
At the moment indirect debugging is not supported but will be available soon.

## Installation

### Server

You must first install the debugger extension and activate the shared library onto the server.  
Follow these [instructions for PgAdmin](https://www.pgadmin.org/docs/pgadmin4/development/debugger.html)

### IDE

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "idea-plpgdebugger"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/ng-galien/idea-plpgdebugger/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
