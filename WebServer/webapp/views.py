from django.views.decorators.csrf import csrf_exempt
from django.shortcuts import render
from django.http import HttpResponse
from django.http import HttpResponseNotFound
from django.core.files import File
from urllib import quote
import MySQLdb as mdb
import time
import json
import redis
import sys
import re

redisClient = redis.StrictRedis(host='127.0.0.1', port=6379, db=0)
targetDir = '/home/hwlee/shared'

@csrf_exempt
def uploadFile(request):
    sourceFile = request.FILES['file']
    if sourceFile is None:
        return HttpResponseNotFound('Uploaded file not exists.')
    if sourceFile.content_type[0:5] != 'video':
        return HttpResponseNotFound(sourceFile.content_type)
    targetPrefix = str(int(time.time() * 1000))
    targetName = targetPrefix + "_" + quote(sourceFile.name.encode('utf-8'))
    targetPath = targetDir + '/' + targetName
    with open(targetPath, 'wb+') as destination:
        for chunk in sourceFile.chunks():
            destination.write(chunk)
    redisClient.publish('DASH', 'BATCH ' + targetPath)
    return HttpResponse(json.dumps({}))

@csrf_exempt
def getCpuStat(request):
    with open('/proc/stat') as fd:
        cpustats = []
        while True:
            line = fd.readline()
            if len(line) == 0:
                break;
            if line[0:3] == 'cpu':
                cpustats.append(line.replace('\n', ''))
    return HttpResponse(json.dumps(cpustats))

@csrf_exempt
def getNetworkStat(request):
    dev = request.REQUEST['dev']
    netstats = []
    with open('/proc/net/dev') as fd:
        while True:
            line = fd.readline().strip()
            if len(line) == 0:
                break;
            if line[0:len(dev)] == dev:
                netstats.append(line.replace('\n', ''))
    return HttpResponse(json.dumps(netstats))

@csrf_exempt
def getDiskStat(request):
    dev = request.REQUEST['dev']
    diskstats = []
    with open('/proc/diskstats') as fd:
        while True:
            line = re.sub(' +', ' ', fd.readline().strip()).split(' ')
            line = ' '.join(line[2:len(line)])
            if len(line) == 0:
                break;
            if line[0:len(dev)] == dev:
                diskstats.append(line.replace('\n', ''))
    return HttpResponse(json.dumps(diskstats))

@csrf_exempt
def getMediaList(request):
    data = []
    conn = mdb.connect('127.0.0.1', 'root', '0000', 'dash')
    cursor = conn.cursor()
    rows = cursor.execute('select * from tbl_media order by media_id desc')
    for i in range(0, rows):
        data.append(cursor.fetchone())
    cursor.close()
    conn.close()
    return HttpResponse(json.dumps(data))

@csrf_exempt
def signIn(request):
    email = request.REQUEST['email']
    password = request.REQUEST['password']
    if email == None or password == None:
       return HttpResponseNotFound(json.dumps({}))
    session = redisClient.hget('SESSION', email)
    if session != None:
       return HttpResponse(json.dumps({}))
    conn = mdb.connect('127.0.0.1', 'root', '0000', 'dash')
    cursor = conn.cursor()
    ret = cursor.execute('select usr_id, usr_nm from tbl_user where usr_id = %s and passwd = %s', [email, password])
    if ret != 1L:
       return HttpResponseNotFound(json.dumps({}))
    data = cursor.fetchone()
    redisClient.hset('SESSION', email, data)
    cursor.close()
    conn.close()
    return HttpResponse(json.dumps(data))

@csrf_exempt
def signOut(request):
    email = request.REQUEST['email']
    if email != None:
        redisClient.hdel('SESSION', email)
    return HttpResponse(json.dumps({}))

@csrf_exempt
def signUp(request):
    email = request.REQUEST['email']
    name = request.REQUEST['name']
    password = request.REQUEST['password']
    if email == None or name == None or password == None:
       return HttpResponseNotFound(json.dumps({}))
    conn = mdb.connect('127.0.0.1', 'root', '0000', 'dash')
    cursor = conn.cursor()
    ret = cursor.execute('select * from tbl_user where usr_id = %s', [email])
    if ret == 1L:
       return HttpResponseNotFound(json.dumps({}))
    cursor.execute('insert into tbl_user values (%s, %s, %s)', [email, name, password])
    cursor.close()
    conn.commit()
    conn.close()
    return HttpResponse(json.dumps({}))
