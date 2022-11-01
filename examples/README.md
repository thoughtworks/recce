# Examples

Examples can be managed with a "convention over configuration" approach for development using Batect.

## Layout

```
├── batect-petshop-mariadb-yml               # Batect configuration for the scenario - which databases to start and its name
|
├── db                                       # Batect configuration for starting and migrating various databases
│   ├── batect-source-mariadb.yml
│   └── batect-target-mariadb.yml
|
└── scenario                                 # Scenarios go here
    └── petshop-mariadb                      # Scenario called "petshop-mariadb"
        ├── application-petshop-mariadb.yml  # Micronaut configuration fragment for this scenario 
        ├── source                           # Flyway migrations to apply to the "source" schema
        │   └── V1__CREATE.sql
        └── target                           # Flyway migrations to apply to the "target" schema
            └── V1__CREATE.sql
```

## Adding a new scenario

Imagine we are creating a scenario called `example-scenario`:

1. Decide what *databases* you need for your scenario.
2. Create a `batect-${example-scenario}.yml` in the `examples/` folder, which includes the batect config includes of source and target DBs you want.
3. Create a new folder with the name of your scenario in the `scenario` folder
4. Create `source` and `target` folders which contain *Flyway migrations* appropriate to setup data for your chosen DB
   types
5. Create fragment of *Micronaut configuration* for your datasources and reconciliation datasets
   in `scenario/${name}/application-${name}.yml`
6. Run your scenario's databases alongside Recce with `./batect -f examples/batect-<{example-scenario}.yml run`
7. Assuming everything starts up, invoke a reconciliation for one of your scenarios datasets via the Recce API.
