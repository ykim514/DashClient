from django.conf.urls import include, url
from django.contrib import admin
from django.contrib.staticfiles import views
from webapp.views import *

urlpatterns = [
    url(r'^api/media$', uploadFile),
    url(r'^cpustat$', getCpuStat),
    url(r'^netstat$', getNetworkStat),
    url(r'^diskstat$', getDiskStat),
    url(r'^medias$', getMediaList),
    url(r'^signIn', signIn),
    url(r'^signUp', signUp),
    url(r'^signOut', signOut),
    url(r'^(?P<path>.*)$', views.serve),
]
