Distributed queues
=================================

Loosely ordered distributed in-memory message queue system (with disk persistence) for JSON data as an HTTP service.

Data are split (by default) across all cluster nodes (with random or round robin routing) for better scaling. 

```sh
# creates a queue named 'myQueueName'
curl -X PUT \  
     -H 'AuthToken: xxxx' \
     http://myhost:9000/queues/myQueueName 

# post a json object on queue named 'myQueueName'
curl -X POST 
     -d '{"Hello":"World"}'  \
     -H 'Content-type: application/json' \
     -H 'AuthToken: xxxx' \ 
     http://myhost:9000/queues/myQueueName 

# poll the head of the queue named 'myQueueName'
curl -X GET \   
     -H 'AuthToken: xxxx' \
     http://myhost:9000/queues/myQueueName

# get the size of the queue named 'myQueueName'
curl -X GET \
     -H 'AuthToken: xxxx' \
     http://myhost:9000/queues/myQueueName/size 

# clear the queue named 'myQueueName'
curl -X POST \  
     -H 'AuthToken: xxxx' \
     http://myhost:9000/queues/myQueueName/clear
     
# streaming head of the queue named 'myQueueName'
curl -X GET \
     -N \
     -H 'AuthToken: xxxx' \
     http://myhost:9000/queues/myQueueName/stream

# delete queue named 'myQueueName'
curl -X DELETE \
     -H 'AuthToken: xxxx' \
     http://myhost:9000/queues/myQueueName 

```

You can also consume a queue as SSE stream @ http://myhost:9000/queues/myQueueName/sse
You can also consume and feed a queue as WebSocket @ http://myhost:9000/queues/myQueueName/ws
For SSE and WebSocket you need to provide a `token` param in the URL.

Default token are stored in the application.conf file.

Setup multiple nodes
--------------------

To setup other nodes and add it to the cluster, just copy and deploy the app on another server and change the following configuration

```javascript
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

```javascript
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

Configuration
-------------

You can tune the following flags :

```javascript
distributed-queues {
  file-root = "queues"        # root directory where persistence logs are stored
  node-id = 1                 # id of the node for unique uuid generation in the cluster
  cluster-routing = true      # routing (to nodes) handled by cluster
  compress-every = 102400     # enable log files compression every n read operation
  round-robin-balancer = true # routing with round robin or random
  auto-create-queues = true   # automatically create queue if not exists
}
```

Metrics
----------

You can get metrics as Json through HTTP with 

```
curl http://myhost:9000/metrics.json
```

or through JMX under the `distributed-queues` MBean

CORS
------

You can use it from your browser using CORS :

```html
<h3>Queues CORS test</h3>
<div id="message1"></div>
<br/>
<div id="message2"></div>
<br/>
<button type="button">Try queues</button>
<script type="text/javascript">
    $(function () {
    
        var queueName = "corsQueue";
    
        function blob() {
            return {
                message: "Hello",
                date: new Date().toTimeString() + ""
            };
        }
    
        function addToQueue(blob) {
            return $.ajax({
                url: "http://distributed.queues.com/queues/" + queueName,
                type: "POST",
                crossDomain: true,
                data: JSON.stringify(blob),
                headers: {
                    AuthToken: "oWOYldXFjEZ6qXGRdQuEeLFSMh9MYcbQK9UVI21TRcLotnAVvMWjl6VEvAzIOixd"
                },
                dataType: "json",
                contentType: "application/json",
                success: function (response) {
                    $("#message1").html("Sent to queue : " + JSON.stringify(blob) + ", with correlation id : " + JSON.stringify(response));
                },
                error: function (xhr, status) {
                    $("#message1").html("Error while posting ...");
                }
            });
        }
    
        function fetchFromQueue() {
            return $.ajax({
                url: "http://distributed.queues.com/queues/" + queueName,
                type: "GET",
                crossDomain: true,
                headers: {
                    AuthToken: "oWOYldXFjEZ6qXGRdQuEeLFSMh9MYcbQK9UVI21TRcLotnAVvMWjl6VEvAzIOixd"
                },
                dataType: "json",
                contentType: "application/json",
                success: function (response) {
                    $("#message2").html("Fetched from queue : " + JSON.stringify(response));
                },
                error: function (xhr, status) {
                    $("#message2").html("Error while fetching ...");
                }
            });
        }
    
        $("#sendandfetch").click(function (e) {
            e.preventDefault();
            addToQueue(blob()).then(function() {
                return fetchFromQueue();
            });
        });
    
        $("#fetch").click(function (e) {
            e.preventDefault();
            fetchFromQueue();
        });
    
        $("#post").click(function (e) {
            e.preventDefault();
            addToQueue(blob());
        });
    
        $("#clear").click(function (e) {
            e.preventDefault();
            $("#message1").html('');
            $("#message2").html('');
        });
    });
</script>
```

or if you're lazy just add the js client lib 

```html
 <script src="http://distributed.queues.com/assets/javascripts/distributed-queues.js" type="text/javascript"></script>
```

and just play with it 

```javascript
var queue = DistributedQueues
    .host('http://distributed.queues.com:9000')
    .withTokens("XXXXX", "XXXXX")
    .queue("myqueue");

queue.push({"hello":"world"}).then(function() {
    return queue.size();
}).then(function(size) {
    console.log("queue size : " + size.size);
}).then(function() {
    return queue.poll();
}).then(function(slug) {
    console.log(slug);
    return queue.size();
}).then(function(size) {
    console.log("queue size : " + size.size);
});
```




