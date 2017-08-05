# -*- coding: utf-8 -*-
"""
Created on Sat Dec 31 18:32:27 2016

@author: wjl
"""
import networkx as nx
import matplotlib.pyplot as plt
import urllib2
import json

#获取网络所有设备的列表
url = 'http://localhost:8080/wm/qosoverlldp/device/all/json'
devices = json.loads(urllib2.urlopen(url).read())['devices']
for i in range(0,len(devices)):
    mac=devices[i]['mac']
    host=i+1
    for j in range(len(mac)):#一个主机不可以出现两个mac地址
        host=int(str(mac[j]).replace(':',''),16)
    attachmentPoint=devices[i]['attachmentPoint']
    dev=""
    for j in range(len(attachmentPoint)):
        switch=int(str(attachmentPoint[j]['switch']).replace(':',''),16)
        port=int(attachmentPoint[j]['port'])
        dev='s%d-eth%d'%(switch,port)
    devices[i]=['h%d'%host,dev,switch]
#获取网络所有链路的列表
url = 'http://localhost:8080/wm/topology/links/json'
links = json.loads(urllib2.urlopen(url).read())
linksdict=dict()
switches=set()#点的集合
switchconn=list(tuple())#线的列表
for i in range(len(links)):
    src_switch=int(str(links[i]['src-switch']).replace(':',''),16)
    src='s%d-eth%d'%(src_switch,links[i]['src-port'])
    dst_switch=int(str(links[i]['dst-switch']).replace(':',''),16)
    dst='s%d-eth%d'%(dst_switch,links[i]['dst-port'])
    linksdict.setdefault('%d-%d'%(src_switch,dst_switch),[src,dst])
    if((lambda d:True if d in 'bidirectional' else False)(str(links[i]['direction']))):
        linksdict.setdefault('%d-%d'%(dst_switch,src_switch),[dst,src])
    switches.add(src_switch)
    switches.add(dst_switch)
    switchconn.append((src_switch,dst_switch))
#获取拓扑的交换机链路信息形成图
G = nx.Graph()
for s in switches:
    G.add_node(s)
G.add_edges_from(switchconn)
#pos = nx.spectral_layout(G)
#nx.draw(G,pos,with_labels=True,node_size = 1,font_size=24,font_color='red')
#plt.savefig("Graph.png")
allswpath=nx.all_pairs_shortest_path(G)
for i in range(len(allswpath.values())):
    for j in range(len(allswpath.values()[i])):
        conn=allswpath.values()[i].values()[j]
        conn=(lambda x:[] if len(x) < 2 else x)(conn)
        if len(conn) is 2:
            conn=linksdict['%d-%d'%(conn[0],conn[1])]
        elif(len(conn)>2):
            conn2=list()
            for k in range(len(conn)-1):
                conn2.extend(linksdict['%d-%d'%(conn[k],conn[k+1])])
            conn=conn2
        del allswpath.values()[i].values()[j][:]
        allswpath.values()[i].values()[j].extend(conn)
allpath=list()
for src in devices:
    for dst in devices:
        path=[src[0],src[1]]
        path.extend(allswpath[src[2]][dst[2]])
        path.extend([dst[1],dst[0]])
        allpath.append(path)
for src_sw in switches:
    for dst_sw in switches:
        if len(allswpath[src_sw][dst_sw]) is not 0:
            allpath.append(allswpath[src_sw][dst_sw])
        else:
            allpath.append(['s%d'%src_sw])
print json.dumps(allpath)
