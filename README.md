Digital Ocean Cluster Manager
=============================

Set up and manage your own cluster on [Digital Ocean](http://digitalocean.com) that is highly usable with [Play!](http://www.playframework.com/), [nginx](http://nginx.org/), [MongoDB](http://www.mongodb.org/), [ElasticSearch](http://www.elasticsearch.org/). [Salt](https://github.com/saltstack) is used for orchestration.

Given following [HOCON](https://github.com/typesafehub/config) config

```
digitalOcean {
  clientId: ClientIdFromDigitalOceanControlPanel
  apiKey: ApiKeyFromThereAsWell
}
// second level domain mandatory, must start with dot
baseDomain: .example.org
// node name suffix, mandatory
// if starts with a dot acts like a 3rd level domain
nodeSuffix: .dev
image: Ubuntu 12.04 x64
region: ams1
memory: 512
// will add all matched (containing substring) key names to all nodes
sshKeys: ["key name 1", "key name 2"]
nodes: [
  { name: 1, roles: [common, front, balancer, saltmaster] }
  { name: 2, roles: [common, front] }
  { name: 3, roles: [common, db] }
  { name: 4, roles: [common, db] }
  { name: 5, roles: [common, db] }
]
```

It will set up 5 instances, install Salt master to first one and use it to install required software to other nodes.

If all goes OK — your site will be up and running at www.dev.example.org.
