# Frequently Asked Questions (FAQ)

**Why is this project called Recce?**

It's a play on "rec", short for "reconciliation" and the colloquial British English word "[recce](https://www.dictionary.com/browse/recce)" which is short for "reconnaissance". Recce helps you reconcile data sources by doing query-based reconnaissance.

**How do I pronounce it?**

IPA `/ ˈrɛk i /`
Phonetic `[ rek-ee ]`

**The name is silly. Can we change it?**

Maybe? Please submit suggestions.

**Does Recce scale to hundreds of millions of rows**

Probably not when doing row-by-row, column-by-column comparisons, but It Depends. Would it be useful to try and reconcile row-for-row this much data? What will it tell you if there are 10,000 rows mismatched?

You might want to consider doing row-by-row reconciliations for a subset of data, delimited by time, or domain (subset of), or using aggregate SQL queries to compare, say, counts of #s of rows, grouped by important domain attributes.

**Does Recce scale to millions of rows?**

Sure, but it might be slower than you would like. This will mostly depend on the nature of the databases you are reconciling (and their operational transaction load if you do not either have a reporting DB/read-only replica, or ability to work against a snapshot) as well as Recce's own database spec.

**I don't like SQL in YAML. Is there a better way?**

Neither. And probably. Recce can now load queries from files, either explicitly or based on a convention. Feedback on this approach is welcome!

**Shouldn't this be done with data pipelines and big data technology X?**

Maybe. Many common data migration and monolith-breaking projects on operational systems don't have these huge quantities of data. Furthermore, the migrations are normally done incrementally with one-way or two-way data synchronization of subsets of data as domain boundaries are gradually identified and implemented.

This can mean there is a need to gain confidence that no data has been lost before the data from the old system can be rationalised or removed.

**Why do I need to reconcile data if I use DB data dump + bulk load?**

You possibly don't. This tool is intended for cases where a system, or part of its domain is fully or partially re-implemented. In these cases it is common to load/migrate subsets of data via API to ensure it is semantically valid with the new system. But how to gain additional confidence that no data was lost in the process? Expressing the intended differences and similarities between datasets is one approach to do this.

Sometimes it can be useful to have a separate tool, intended to run with the real data, in a real environment, which is sensitive to the PII or confidentiality of that data - but still gives stakeholders additional confidence in the **completeness** of the migration, and the reliability of any synchronization that is running while systems run in parallel.

**I ran out of memory when setting up the containers.**

Ensure you have at least 3GB allocated to your VM. You can consider allocating more memory to avoid out of memory issues.
