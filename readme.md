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


