# Background

To meet some of the goals in [DESIGN.md](./DESIGN.md) some basic reading was done to look at existing approaches to data
reconciliation.

# Out of Scope

* Commercial solutions - we need something free, open & easy
* Solutions that require a UI to work with - we want something automatable, where results can be reviewed after-the-fact

# Existing reading
* https://docs.broadcom.com/doc/fully-automated-etl-testing-a-step-by-step-guide
* https://icedq.com/data-migration/data-migration-testing-techniques-to-migrate-data-successfully
* https://icedq.com/solutions/data-migration-testing
* https://aws.amazon.com/blogs/big-data/build-a-distributed-big-data-reconciliation-engine-using-amazon-emr-and-amazon-athena/
* https://medium.com/ing-blog/building-trade-level-reconciliation-tool-in-6-weeks-8698f482e334

# Possible existing solutions or inspiration

Commercial
* https://icedq.com/
* https://www.querysurge.com/
* BigEval
* RightData https://getrightdata.com/data-migration.php
* Trifacta

Open Source

* OpenRefine https://github.com/OpenRefine/OpenRefine
  * Local app, running in browser :-/
* DBDiff: https://dbdiff.github.io/DBDiff/

Related

* Existing ETL tooling? Perhaps they have some reconciliation tooling?
  * https://www.stitchdata.com/etldatabase/etl-tools/

# Summary

Most existing tools appear to be designed for

* human interaction with results to "correct" data, e.g
* designed for regular intervention on an ongoing basis by operational users in production, rather than
  migration-assistance tooling
* large, commercial ETL suite tools

There didn't seem to be anything suitable for the type of migrations that we have seen in the wild.