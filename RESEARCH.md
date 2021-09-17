# Background

In legacy migration projects, data migration often has a huge role. In these projects we generally have a need to assure stakeholders that data has not been missed during the migration, and that the integrity of this data is in tact even where it includes sensitive or PII data which is unavailable for the development team, or indeed stakeholders to view.

Hypothesis: There is a lack of simple, easy tools that allow reconciliation between datasets that treat data with the care that it deserves in a sensitive/PII environment, and are suitable for automation.

# Out of Scope

* Commercial solutions - we need something free, open & easy
* Solutions that require a UI to work with - we want something automatable, where results can be reviewed after-the-fact

# Possible features

## Functional 

* Input source + target datasources (SQL)
* Configuration
  * Ignore whitespace <-> null
  * Character encodings
  * Hashing
  * Mapping of tables
* Output
  * Diffs between source and target
    * How to deal with large rowsets?
  * Test assertions
  * Tolerances?

## CFRs

* Suitable for run in production
* Ability to run in hashed/secret mode

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
* 
Open Source
* OpenRefine https://github.com/OpenRefine/OpenRefine
  * Local app, running in browser :-/
* DBDiff: https://dbdiff.github.io/DBDiff/

Related
* Existing ETL tooling? Perhaps they have some reconcilation tooling?
  * https://www.stitchdata.com/etldatabase/etl-tools/