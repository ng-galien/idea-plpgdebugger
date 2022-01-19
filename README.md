# Intellij PL/pg SQL debugger

![Build](https://github.com/ng-galien/idea-plpgdebugger/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

<!-- Plugin description -->
Debug PL/pg SQL in Intellij to speed up and improve productivity with PostgreSQL<br/>
Allow to debug a query from the console like you run it normally: inspect variables and set breakpoints<br/>
The pldbgapi extension must be enabled on the target database.<br/>
This plugin is in early stage of development .
<!-- Plugin description end -->

## Features

* Direct debug from a console
* Code inspection
  * Basic variables
  * Arrays(including array of composite)
* Breakpoints are persisted across sessions

![](img/start.png)

## Limitation

The standard pldbgapi does not send back composite variable, but you can put it in arrays to inspect them.  
A modified extension is available in this [repo](https://github.com/ng-galien/pldebugger), hope this can be integrated in the official extension soon.  
At the moment indirect debugging is not supported but will be available soon.

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "idea-plpgdebugger"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/ng-galien/idea-plpgdebugger/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].



[template]: https://github.com/JetBrains/intellij-platform-plugin-template
