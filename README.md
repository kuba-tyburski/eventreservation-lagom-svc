# About

This is my first try with `lagom`. I highly relied on lagom-scala.g8 and ShoppingCart example.

# API

[OpenAPI](openapi.yaml)

# Database

Application is using Postgres. Configuration is in [application.conf](reservation-impl/src/main/resources/application.conf)

DDL script is provided here: [ddl script](db/ddl.sql)

Before running the app set up database, create tables, adjust db configuration.

# Running

From main directory (where the [build.sbt](build.sbt) is) run:
```sbt runAll```

# Examples

* create new record
```
curl -H "Content-Type: application/json" -d '{"quantity":5}' -X POST http://localhost:9000/api/event/123/customer/123
```

* extend (renew) reservation 
```
curl -X POST http://localhost:9000/api/event/123/customer/123/reservation/123/extend
```

* cancel reservation
```
curl -X DELETE http://localhost:9000/api/event/123/customer/123/reservation/3c0bcb00-0894-4aea-b64f-2258877a0bfa
```

* get all customer reservations
```
curl http://localhost:9000/api/customer/123
```

* get all reservations
```
curl http://localhost:9000/api/admin/event
```

* get customer reservation for event
```
curl http://localhost:9000/api/event/123/customer/123
```

* get all reservations for event
```
curl http://localhost:9000/api/admin/event/123
```

# What needs to be done

* Additional reservation state `Expired`
* Scheduler to automatically expire reservations
* Fix enum storage witch e.g. `KebsEnum` (couldn't make it work for now, some issues with using enum field in slick filters)
* introduce authorization with e.g. `JWT`
* probably many more...
