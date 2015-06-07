from Daemon import Daemon
from BatchListener import BatchListener
import logging

logging.basicConfig(level=logging.DEBUG, format='[%(levelname)s] %(asctime)s %(name)s - %(module)s.%(funcName)s, %(message)s')

daemon = Daemon()
daemon += BatchListener()
daemon.start()
