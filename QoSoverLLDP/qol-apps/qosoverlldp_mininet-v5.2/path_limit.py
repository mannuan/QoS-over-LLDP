# -*- coding: utf-8 -*-
"""
Created on Sat May 20 17:59:11 2017

@author: mininet
"""
import urllib2 
import json

nodelist = str(raw_input('请输入路径限制节点:')).split(',')
dijkstra_time = 0
all_shortest_time = 0
for i in range(len(nodelist)-1):
    url = 'http://127.0.0.1:8080/wm/qosoverlldp/shortestroutewithtime/%s/%s/json'%(nodelist[i],nodelist[i+1])
    Json = json.loads(urllib2.urlopen(url).read(),encoding='utf-8')
    dijkstra_time += int(str(Json[len(Json)-2]['dijkstra_path_time']).replace('ms',''))
    all_shortest_time += int(str(Json[len(Json)-1]['all_shortest_path_time']).replace('ms',''))
print ('迪杰斯特拉算法耗时:%dms，全路径算法耗时:%dms')%(dijkstra_time,all_shortest_time)
