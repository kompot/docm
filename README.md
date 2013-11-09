Digital Ocean Cluster Manager
=============================

Set up and manage your own cluster on [Digital Ocean](http://digitalocean.com) that is highly usable with [Play!](http://www.playframework.com/), [nginx](http://nginx.org/), [MongoDB](http://www.mongodb.org/), [ElasticSearch](http://www.elasticsearch.org/). [Salt](https://github.com/saltstack) is used for orchestration.

Given following [HOCON](https://github.com/typesafehub/config) config

```
digitalOcean:
    clientId: ClientIdFromDigitalOceanControlPanel
    apiKey: ApiKeyFromThereAsWell
baseDomain: example.org
nodeNameSuffix: sub
os: Ubuntu 13.10 x32 
datacenter: Amsterdam 1
memory: 512MB
nodes
  1:
    roles: balancer, front, saltmaster
  2:
    roles: front
  3:
    roles: db
    os: Ubuntu 13.10 x64
  4:
    roles: db
    os: Ubuntu 13.10 x64
  5:
    roles: db
    os: Ubuntu 13.10 x64
```

It will set up 5 instances, install Salt master to first one and use to install required software to other nodes.

If all goes OK — your site will be up and running at www.sub.example.org.