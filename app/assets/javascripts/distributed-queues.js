var DistributedQueues = DistributedQueues || {};
(function(exports) {

    function ajaxCall(host, suffix, name, verb, token, success, failure, blob) {
        var config = {
            url: host + "/queues/" + name + suffix,
            type: verb,
            crossDomain: true,
            headers: {
                AuthToken: token
            },
            dataType: "json",
            contentType: "application/json",
            success: function (response) {
                if (typeof success !== 'undefined') success(response);
            },
            error: function (xhr, status) {
                if (typeof failure !== 'undefined') failure(xhr, status);
            }
        };
        if (typeof blob !== 'undefined') {
            config.data = JSON.stringify(blob);
        }
        return $.ajax(config);
    }

    exports.host = function(host) {
        var hostApi = {};
        hostApi.withTokens = function(apiToken, adminToken) {
            var tokensApi = {};
            tokensApi.queue = function(name) {
                var queueApi = {};
                queueApi.push = function(blob, success, failure) {
                    if (typeof success === 'undefined') success = function() {};
                    if (typeof failure === 'undefined') failure = function() {};
                    return ajaxCall(host, "", name, 'POST', apiToken, success, failure, blob);
                };
                queueApi.poll = function(success, failure) {
                    if (typeof success === 'undefined') success = function() {};
                    if (typeof failure === 'undefined') failure = function() {};
                    return ajaxCall(host, "", name, 'GET', apiToken, success, failure);
                };
                queueApi.size = function(success, failure) {
                    if (typeof success === 'undefined') success = function() {};
                    if (typeof failure === 'undefined') failure = function() {};
                    return ajaxCall(host, "/size", name, 'GET', apiToken, success, failure);
                };
                queueApi.clear = function(success, failure) {
                    if (typeof success === 'undefined') success = function() {};
                    if (typeof failure === 'undefined') failure = function() {};
                    return ajaxCall(host, "/clear", name, 'POST', adminToken, success, failure);
                };
                queueApi.delete = function(success, failure) {
                    if (typeof success === 'undefined') success = function() {};
                    if (typeof failure === 'undefined') failure = function() {};
                    return DistributedQueues.host(host).withTokens(apiToken, adminToken).delete(success, failure);
                };
                return queueApi;
            };
            tokensApi.create = function(name, success, failure) {
                if (typeof success === 'undefined') success = function() {};
                if (typeof failure === 'undefined') failure = function() {};
                ajaxCall(host, "", name, 'PUT', adminToken, success, failure);
                return DistributedQueues.host(host).withTokens(apiToken, adminToken).queue(name);
            };
            tokensApi.delete = function(name, success, failure) {
                if (typeof success === 'undefined') success = function() {};
                if (typeof failure === 'undefined') failure = function() {};
                return ajaxCall(host, "", name, 'DELETE', adminToken, success, failure);
            };
            return tokensApi;
        };
        return hostApi;
    };
})(DistributedQueues);