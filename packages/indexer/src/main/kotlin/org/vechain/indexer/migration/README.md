# Database migration

This package contains the change units responsible for creating and/or migrating the database schema and data.
It's powered by `Mongock` (Cf. https://docs.mongock.io/).

It's intended to provide a code-first approach for db migrations, enabling code and database changes to be shipped 
together and be kept in sync at all times.

## How it works

- Mongock is autoconfigured via its spring boot starter, and it applies db changes through the spring data mongodb driver.
- To apply a new migration, simply add a new `@ChangeUnit` class in a sub-package named with the version of the code it's applied to.
- Mongock maintains a changelog collections to keep track of applied migrations, and loads new ones.
- The mongock runner obtains a pessimistic lock for the duration of the change unit migration, so it's safe to be used in a distributed env.
- The migration is applied on spring startup; in case of migration failure, the application simply does not complete startup.

## Change units guidelines

- For new code version, change units must be placed in a new sub-package of the migration package with the same version.
- Change units should ideally be idempotent, allowing for them to be interrupted and resumed at any time. This means checking for collection existence, and ensuring idx existence, instead of just creating them.
- Change units should be kept backwards compatible as much as possible.
- Change units should be kept light & fast, so as not to present a risk to application's startup. New indexes in heavy collections in particular should be created in the background.
