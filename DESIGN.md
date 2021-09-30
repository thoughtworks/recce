# Background

In legacy migration projects, data migration often has a huge role. In these projects we generally have a need to assure
stakeholders that data has not been missed during the migration, and that the integrity of this data is in tact even
where it includes sensitive or PII data which is unavailable for the development team, or indeed stakeholders to view.

Furthermore, during such migrations it is common that

* a monolithic service and database is being split into multiple services/databases, following Domain Driven Design
    * thus there is no trivial approach to reconciliation, since parts of the source schema may be split across multiple
      target schemas, sometimes with different keys
* the teams performing the redesign and migration

There is a lack of simple, flexible tools that allow reconciliation between datasets that treat data with the care that
it deserves in a sensitive/PII environment, and are suitable for automation.

# Assumptions

* users are developers, and will be using the output of the tool to either
    * correct data migration scripts; or
    * correct data synchronization logic between source and target; or
    * correct business/persistence logic in target system; or
    * otherwise identify how the data has become out of sync and use automation to correct it in software

# Desirable Attributes

* **runs as a service** rather than CLI tool, to allow for
    * non-intervention scheduled runs at off-peak load times
    * ability to interact with the tool via API to trigger adhoc runs
* **SQL-driven**
    * developers re-writing a service, migrating data or splitting a monolith are assumed to be familiar with the data
      models on both source and target, and can thus express these rules as SQL, joining tables as appropriate
* should allow reconciling a new dataset **purely through configuration**; without writing non-SQL code
* should allow reconciling **row+column level data**
* should allow reconciling **aggregates**
    * it is likely to be unrealistic to reconcile very large tables with 100M+ rows on a row-by-row, column-by-column
      basis
    * allowing developers to express aggregate queries that must match on both sides is likely sufficient to be able to
      achieve this
* should allow for reconciliation to take place daily to
* datasets to reconcile should be able to be **sliced** by developers for performance reasons
    * by domain concept (e.g user group/class)
    * by time (month-by-month or day-by-day)
* Suitable for running in production
* Should be compare tables with ~30-40 million rows at column level without use of aggregates
  * Obvious caveat is that it depends on the dataset query and source/target table design
  * Intent is that the reconciliation tool and its own database should not be the blocker here

# Other possible features

* ability to compare a CSV export to a dataset from a live DB

# High Level Design Considerations

* should have its own database where reconciliation results are stored
  * in order to avoid having to do a massive, un-scalable in-memory merge or map-reduce
  * to allow for querying results over time and interacting with them
* the user will need to designate a **migration primary key** that is common across source and target data sources for
  comparison
  * this key will be assumed to not contain PII/sensitive data
* data from both source+target rows should generally be compared by hashes; to avoid persistence of PII data inside the
  database
  * this will lead to some loss of specificity when understanding why a row does not match
  * it may be possible to directly compare data, but this should be on an opt-in basis (private by default -> compare
    hashes)
* Expressing intended differences in storage between source and target will be done in SQL by developer e.g.
  * a NUMBER enumeration converted to a VARCHAR enumeration
  * numerical tolerance differences
  * character encoding differences
* API would primarily be used to trigger an existing pre-configured reconciliation dataset
  * as opposed to sending details/SQL and data source definitions for an entire new dataset
  * ensures that the reconciliation
* Approach scaling through running multiple threads executing various queries in the background via connection pools
  rather than multiple pods/container instances
  * Avoids complexity in scheduling, and we want this tool to be relatively simple to setup and use