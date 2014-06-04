$(function () {

    var queueName = "corsQueue";
    var corsQueue = DistributedQueues
        .host('http://distributed.queues.com:9000')
        .withTokens("oWOYldXFjEZ6qXGRdQuEeLFSMh9MYcbQK9UVI21TRcLotnAVvMWjl6VEvAzIOixd", "gPYwwbJqLStr24N59WKkldayfqRJPmPTYcy49FPXxIvevjSt0uaSrd25tddfpD8e")
        .queue(queueName);

    function blob() {
        return {
            message: "Hello",
            date: new Date().toTimeString() + ""
        };
    }

    function success1(blob) {
        return function (response) {
            $("#message1").html("Sent to queue (" + JSON.stringify(blob) + ") with correlation id : " + JSON.stringify(response));
        };
    }

    function failure1(xhr, status) {
        $("#message1").html("Error while posting ...");
    }

    function success2(response) {
        $("#message2").html("Fetched from queue : " + JSON.stringify(response));
    }

    function failure2(xhr, status) {
        $("#message2").html("Error while fetching ...");
    }

    $("#sendandfetch").click(function (e) {
        e.preventDefault();
        var b = blob();
        corsQueue.push(b, success1(b), failure1).then(function() {
            return corsQueue.poll(success2, failure2);
        });
    });

    $("#fetch").click(function (e) {
        e.preventDefault();
        corsQueue.poll(success2, failure2);
    });

    $("#post").click(function (e) {
        e.preventDefault();
        var b = blob();
        corsQueue.push(b, success1(b), failure1);
    });

    $("#clear").click(function (e) {
        e.preventDefault();
        $("#message1").html('');
        $("#message2").html('');
    });
});