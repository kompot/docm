vim:
  pkg:
    - installed

nano:
  pkg:
    - removed

mc:
  pkg:
    - installed

salt-minion:
  service.running:
    - watch:
      - file: /etc/salt/minion.d/networkinfo.conf
  file.managed:
    - name: /etc/salt/minion.d/networkinfo.conf
    - source: salt://etc/salt/minion.d/networkinfo.conf

# should watch updated IPs and update all hosts on all nodes
/etc/hosts:
  file.managed:
    - source: salt://etc/hosts
    - template: jinja
