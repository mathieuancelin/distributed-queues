Distributed queues
=================================

Loosely ordered (or not) distributed message queue system with disk persistence as an HTTP service.

```
# creates a queue named 'myQueueName'
curl -X PUT    -H 'AuthToken: xxxx' http://myhost:9000/queues/myQueueName 

# post a json object on queue named 'myQueueName'
curl -X POST   -H 'AuthToken: xxxx' http://myhost:9000/queues/myQueueName 

# poll the head of the queue named 'myQueueName'
curl -X GET    -H 'AuthToken: xxxx' http://myhost:9000/queues/myQueueName

# delete queue named 'myQueueName'
curl -X DELETE -H 'AuthToken: xxxx' http://myhost:9000/queues/myQueueName 
```

Setup multiple node
-------------------

To setup other nodes and add it to the cluster, just copy and deploy the app on another server and change the following configuration

```
distributed-queues {
  node-id = CHANGE_ID_NUMBER_HERE 
}

akka {
  remote {
    netty.tcp {
      port = 0  # CHANGE_VALUE_TO_0
    }
  }
  cluster {
    seed-nodes = [
      "akka.tcp://queues-system@HOST_OF_THE_FIRST_NODE:2551"
    ]
  }
}
```

you can also setup other seed node (see http://doc.akka.io/docs/akka/2.3.3/scala/cluster-usage.html#joining-to-seed-nodes).
You just have to set `akka.remote.netty.tcp.port=2551` or any other value. And to reference that particular seed node 
from other nodes, just add its address to `akka.cluster.seed-nodes` like :

```
akka {
  cluster {
    seed-nodes = [
      "akka.tcp://queues-system@HOST_OF_THE_FIRST_NODE:2551",
      "akka.tcp://queues-system@HOST_OF_THE_SECOND_NODE:2552",
      "akka.tcp://queues-system@HOST_OF_THE_THIRD_NODE:2553",
      "akka.tcp://queues-system@HOST_OF_THE_FOURTH_NODE:2554"
    ]
  }
}
```


