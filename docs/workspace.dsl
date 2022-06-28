workspace {

    model {
        dev = person "Developer" "Dev wanting to reconcile datasources"

        sourceDb = softwareSystem "Source SQL DB" "Source SQL DB" "ExtDatabase"
        targetDb = softwareSystem "Target SQL DB" "Target SQL DB" "ExtDatabase"

        recceConfig = softwareSystem "Dataset Configurations" "YAML & SQL Dataset Configurations" "Files"
        recceDb = softwareSystem "Recce DB" "Recce PostgreSQL DB" "Database"
        recceServer = softwareSystem "Recce Server" "Recce Server" {

            server = container "Server" {
                api = component "API"
                recRunner = component "Rec Run Service"

                api -> recRunner "Trigger"
            }

            -> recceConfig "Load pre-configured dataset query configuration"
            -> recceDb "Persist dataset row hashes"
            -> recceDb "Compute reconciliation result summary"
            -> sourceDb "Load dataset(s) from source"
            -> targetDb "Load dataset(s) from target"

        }

        dev -> recceConfig "Configures source & target DB dataset queries"
        dev -> recceServer "Triggers reconciliations adhoc via API"
        recceServer -> dev "Reconciliation result summary"
        recceServer -> recceServer "Triggers scheduled reconciliations"
    }

    views {
        systemContext recceServer "SystemContext" {
            include *
            autoLayout
        }

        container recceServer "ContainerView" {
            include *
            autoLayout
        }

        component server "ComponentView" {
            include *
            autoLayout
        }

        styles {
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }
            element "Database" {
                shape Cylinder
            }
            element "ExtDatabase" {
                shape Cylinder
                background #5dbb63
            }
            element "Files" {
                shape Folder
            }
        }
    }

}
