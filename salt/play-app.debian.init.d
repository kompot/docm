 #! /bin/sh

### BEGIN INIT INFO
# Provides:          play
# Required-Start:    $all
# Required-Stop:     $all
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description:
# Description:
### END INIT INFO

APP="docm-play-example"
APP_PATH="/usr/share/$APP"

start() {
    $APP_PATH/bin/$APP -mem 256 &
}

stop() {
    kill `cat $APP_PATH/RUNNING_PID`
}

case "$1" in
  start)
    echo "Starting $APP"
    start
    echo "$APP started."
    ;;
  stop)
    echo "Stopping $APP"
    stop
    echo "$APP stopped."
    ;;
  restart)
    echo  "Restarting $APP."
    stop
    sleep 2
    start
    echo "$APP restarted."
    ;;
  *)
    N=/etc/init.d/play.$APP
    echo "Usage: $N {start|stop|restart}" >&2
    exit 1
    ;;
esac

exit 0
