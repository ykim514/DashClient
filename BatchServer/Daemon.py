import logging
import redis

class Daemon:
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self.redis = redis.StrictRedis(host='127.0.0.1', port=6379, db=0)
        self.pubsub = self.redis.pubsub()
        self.pubsub.subscribe('DASH')
        self.listeners = {}
    def __iadd__(self, listener):
        self.listeners[listener.command] = listener
        listener.daemon = True
        return self
    def start(self):
        self.logger.info('daemon start')
        for command in self.listeners:
            self.logger.info('listener start: ' + command)
            self.listeners[command].start()
        self.logger.info('daemon initialization success')
        while True:
            for item in self.pubsub.listen():
                if item['data'] == 1L:
                    self.logger.info('connect to redis server success')
                    self.logger.info('wait for "DASH" publication')
                    continue
                try:
                    command, argument = item['data'].split(' ', 1)
                    listener = self.listeners[command]
                    if listener != None:
                        listener.queue.put(argument)
                    else:
                        self.logger.info('not find listener: "%s"', command)
                except Exception as e:
                    self.logger.error(e)
