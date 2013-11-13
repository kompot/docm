mongodb-10gen:
  cmd.run:
    - name: sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10
    - unless: apt-key list | grep -q 7F0CEB10
    - require:
      - file: /etc/apt/sources.list.d/10gen.list
  file:
    - managed
    - name: /etc/apt/sources.list.d/10gen.list
    - source: salt://mongodb/10gen.list
    - skip_verify: True
  pkg:
    - installed
    - refresh: True
    - skip_verify: True

mongodb:
  service.running:
    - watch:
      - file: mongodb-config

mongodb-config:
  file.managed:
    - name: /etc/mongodb.conf
    - source: salt://mongodb/mongodb.conf
    - template: jinja
    - require:
      - pkg: mongodb-10gen

/usr/libexec/mongo/repset_init.js:
  file:
    - managed
    - source: salt://mongodb/repset.js
    - template: jinja

/usr/libexec/mongo/check_mongo_status.sh:
  file:
    - managed
    - source: salt://mongodb/check_mongo_status.sh
    - mode: 755

# mongo --quiet
mongo /usr/libexec/mongo/repset_init.js:
  cmd:
    - run
    - unless: /usr/libexec/mongo/check_mongo_status.sh
    - user: root
    - group: root
    - require:
      - service: mongodb
      - file: /usr/libexec/mongo/check_mongo_status.sh
