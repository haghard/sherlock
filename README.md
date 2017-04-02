# Sherlock

Sherlock is service discovery implemented to be AP (from CAP). To do this, it uses Akka with Distributed Data (CRDT).

Start with:

```
# First seed node with JMX enabled to acces cluster management through the client
sbt run -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -DAKKA_PORT=2552 -DPORT=9090
# Second seed node
sbt run -DAKKA_PORT=2553 -DPORT=9091
# Next random node
sbt run -DAKKA_PORT=0 -DPORT=0
```


curl -X POST -d '{"ip":"192.168.0.1","path":"/users/v1.0","port":9000}' --header "Content-Type:application/json" http://localhost:9091/service --include
curl -X POST -d '{"ip":"192.168.0.2","path":"/users/v1.0","port":9000}' --header "Content-Type:application/json" http://localhost:9091/service --include


http GET :9091/service/users/v1.0

{
    "accuracy": {
        "192.168.0.11:9000": 0.25345377958260806,
        "192.168.0.12:9000": 1.316531107211395
    }
}


Presentation
  https://docs.google.com/presentation/d/1w39pan2HrwSriJGr2cuexIPuWLtS7BlA7d5N8evNfWE/pub?slide=id.p