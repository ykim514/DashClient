from threading import Thread
from subprocess import call
from Queue import Queue
import os
import xml.etree.ElementTree as ET
import logging
import time
import traceback
import MySQLdb as mdb

class BatchListener(Thread):
    def __init__(self):
        Thread.__init__(self)
        self.logger = logging.getLogger(__name__)
        self.queue = Queue()
        self.command = 'BATCH'
        self.heights = [ 720, 540, 360 ]
        self.bitrates = [ 320, 32 ]
    def run(self, *args, **kwargs):
        while True:
            sourcePath = self.queue.get()
            if not os.path.exists(sourcePath):
                self.logger.info('source file not exists')
                continue
            try:
                sourceDir, sourceFileName = sourcePath.rsplit('/', 1)
                targetPrefix = str(int(time.time() * 1000))
                targetFileName = targetPrefix + '.mp4'
                dashFileNameList = []
                self.logger.info('begin transcode ' + sourcePath)
                ret = self.transcode(sourcePath, targetFileName, 'h264', 'libfdk_aac')
                if ret != 0:
                    raise Exception('transcode media failure')
                for height in self.heights:
                    self.logger.info('begin extract video: ' + str(height) + 'p')
                    resultFileName = self.extractVideo(sourcePath, targetPrefix, height / 9 * 16, height)
                    if resultFileName == None:
                        raise Exception('extract video failure')
                    dashFileNameList.append(resultFileName)
                for bitrate in self.bitrates:
                    self.logger.info('begin extract audio: ' + str(bitrate) + 'k')
                    resultFileName = self.extractAudio(sourcePath, targetPrefix, bitrate)
                    if resultFileName == None:
                        raise Exception('extract audio failure')
                    dashFileNameList.append(resultFileName)
                self.logger.info('split video & audio: ' + str(dashFileNameList))
                ret = self.dash(sourceDir, dashFileNameList, targetPrefix, 5000)
                if ret != 0:
                    raise Exception('dash multiple media failure')
                self.logger.info('rename original mpd file: ' + targetPrefix + '.mpd.old')
                os.rename(targetPrefix + '.mpd', targetPrefix + '.mpd.old')
                self.logger.info('remake mpd file: ' + targetPrefix + '.mpd')
                self.remake(targetPrefix + '.mpd.old', targetPrefix + '.mpd')
                self.logger.info('update database')
                self.updateDatabase(sourceDir, sourceFileName, targetPrefix + '.mpd')
                self.logger.info('batch complete!')
            except Exception as e:
                traceback.print_exc()
    def transcode(self, sourcePath, targetFileName, vcodec, acodec):
        if not os.path.exists(sourcePath):
            raise Exception('source file not exists')
        sourceDir, sourceFileName = sourcePath.rsplit('/', 1)
        if not os.path.exists(sourceDir):
            self.logger.info('to create output directory: %s', sourceDir)
            os.mkdir(sourceDir)
        os.chdir(sourceDir)
        ret = call(['ffmpeg', '-i', sourceFileName, '-vcodec', vcodec, '-acodec', acodec, targetFileName])
        return ret
    def extractVideo(self, sourcePath, targetPrefix, width, height):
        if not os.path.exists(sourcePath):
            return False
        sourceDir, sourceFileName = sourcePath.rsplit('/', 1)
        targetFileName = targetPrefix + '_' + str(height) + 'p.m4v'
        ret = call(['ffmpeg', '-i', sourcePath, '-s', str(width) + 'x' + str(height), '-an', sourceDir + '/' + targetFileName])
        if ret != 0:
            return None
        else:
            return targetFileName
    def extractAudio(self, sourcePath, targetPrefix, bitrate):
        if not os.path.exists(sourcePath):
            return False
        sourceDir, sourceFileName = sourcePath.rsplit('/', 1)
        targetFileName = targetPrefix + '_' + str(bitrate) + 'k.m4a'
        ret = call(['ffmpeg', '-i', sourcePath, '-b:a', str(bitrate) + 'k', '-vn', sourceDir + '/' + targetFileName])
        if ret != 0:
            return None
        else:
            return targetFileName
    def dash(self, sourceDir, sourceFileNameList, targetPrefix, duration):
        for sourceFileName in sourceFileNameList:
            if not os.path.exists(sourceDir + '/' + sourceFileName):
                raise Exception('source file not exists: ' + sourceDir + '/' + sourceFileName)
        targetFileName = targetPrefix + '.mpd'
        os.chdir(sourceDir)
        ret = call(['MP4Box', '-dash', str(duration), '-split', '0',
            '-url-template', '-out', targetFileName] + sourceFileNameList)
        return ret
    def remake(self, sourcePath, targetPath):
        namespace = 'urn:mpeg:dash:schema:mpd:2011'
        ET.register_namespace('', namespace)
        tree = ET.parse(sourcePath)
        root = tree.getroot()
        for parent in root.iter('{' + namespace + '}AdaptationSet'):
            child = parent.iter('{' + namespace + '}SegmentTemplate').next()
            initialization = child.get('initialization')
            parent.remove(child)
            for child in parent.iter('{' + namespace + '}SegmentTemplate'):
                child.set('initialization', initialization)
        tree.write(targetPath, encoding='utf-8', xml_declaration=True)
    def updateDatabase(self, directory, sourceFileName, targetFileName):
        # targetPath = directory + '/' + targetFileName
        sourceFileName = sourceFileName.split('_', 1)[1]
        targetPath = targetFileName
        # targetFileSize = os.stat(targetPath).st_size
        targetFileSize = directory + '/' + targetFileName.rsplit('.', 1)[0] + '.mp4'
        conn = mdb.connect('127.0.0.1', 'root', '0000', 'dash')
        cursor = conn.cursor()
        cursor.execute('insert into tbl_media values (default, %s, %s, %s)',
            [sourceFileName, targetFileSize, targetPath])
        conn.commit()
        conn.close()
