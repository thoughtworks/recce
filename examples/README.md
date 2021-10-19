# Examples

Examples can be managed with a "convention over configuration" approach for development using Batect.

## Layout

```
├── db                                       # Batect configuration for starting and migrating various databases
│   ├── batect-source-mysql.yml
│   └── batect-target-mysql.yml
└── scenario                                 # Scenarios go here
    └── simple-mysql                         # Scenario called "simple-mysql"
        ├── application-simple-mysql.yml     # Micronaut configuration fragment for this scenario 
        ├── source                           # Flyway migrations to apply to the "source" schema
        │   └── V1__CREATE_SCHEMA.sql
        └── target                           # Flyway migrations to apply to the "target" schema
            └── V1__CREATE_SCHEMA.sql
```

## Adding a new scenario

1. Decide what *databases* you need for your scenario. You can modify the `includes` in [../batect.yml](../batect.yml)
   to ensure they are started and migrated for your intended scenario.
2. Create a new folder with the name of your scenario in the `scenario` folder
3. Create `source` and `target` folders which contain *Flyway migrations* appropriate to setup data for your chosen DB
   types
4. Create fragment of *Micronaut configuration* for your datasources and reconcilation datasets
   in `scenario/${name}/applicatiomn-$name}.yml`