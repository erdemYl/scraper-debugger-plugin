# ScraperFlow-Debugger

[![build](https://img.shields.io/badge/build-maven-lightblue.svg)](https://maven.apache.org/plugins/index.html)

Desktop debugger tool for [ScraperFlow](https://github.com/scraperflow/scraperflow) programming framework. Provided as plugin via `jar` files. 

# Overview
![screenshot](doc/Overview.jpg)

* `A:` Control-flow graph. `ctrl + left-click` to navigate. `ctrl + shift + left-click` to set breakpoint.
* `B:` `execution`, `step` and `continue` buttons for all flows.
* `C:` Dynamic flow graph. `left-click` to inspect flow-map. `ctrl + left-click` to inspect flow states.
* `D:` Shows flow states or the flow-map for one selected flow.
* `E:` `step` and `continue` buttons for one selected flow.



# Quickstart - Debugging
Use the start script in `~/opt/scraperflow`
with `debug` argument:

* `./scraperflow file.yml debug`


# Quickstart - Java
Execute
* `mvn verify` for installing debugger to `~/opt/scraperflow/var` folder

or
* `mvn package` for packaging the distribution in the project target folders.

# Debugger Limits
* Program `cfg` should form a tree.
* Tree should have `height < 20` and  `width < 16`.
* No imported instances.
* Nodes should be configured to provide enough threads.
* Nodes should have distinct service groups.
