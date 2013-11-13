openjdk-7-jre-headless:
  pkg:
    - installed

docm-play-example:
  pkg.installed:
    - sources:
      - docm-play-example: salt://docm-play-example-0.0.1.deb

/etc/init.d/play.docm:
  file.managed:
    - source: salt://play-app.debian.init.d
    - template: jinja
    - user: root
    - group: root
    - mode: 750

play.docm:
  service:
    - running
