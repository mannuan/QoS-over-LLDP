# -*- coding: utf-8 -*-
"""
Created on Sat Dec 31 18:32:27 2016

@author: wjl
"""
import networkx as nx
import matplotlib.pyplot as plt
import urllib2
import json
import sys,datetime
from itertools import islice

def Get_SrcSw_DstSw(src_host,dst_host,Error_list,shortest_path,begin_insert):
    #获取网络所有设备的列表
    url = 'http://localhost:8080/wm/qosoverlldp/device/all/json'
    device = json.loads(urllib2.urlopen(url).read())['devices']
    #获取主机与交换机的关系
    host_sw = dict()
    sw_port = dict()
    for dev in device:
        host = None
        sw = None
        for h in dev['mac']:
            if h is not None:
                host = int(h.replace(':',''),16)
        for s in dev['attachmentPoint']:
            if s is not None:
                sw = int(s['switch'].replace(':',''),16)
                port = int(s['port'])
                sw_port.setdefault(host,port)
        host_sw.setdefault(host,sw)
    src_sw, dst_sw = None, None
    if 's' in src_host:#如果源主机是交换机
        src_sw = int(src_host.replace('s',''))
    elif 'h' in src_host:
        src_host = int(src_host.replace('h',''))
        try:
            src_sw = host_sw[src_host]
            shortest_path.append('h'+str(src_host))
            shortest_path.append('s'+str(src_sw)+'-eth'+str(sw_port[src_host]))
        except:
            shortest_path = []
            Error_list.append('h'+str(src_host)+' is nonexist')
    begin_insert=len(shortest_path)
    if 's' in dst_host:
        dst_sw = int(dst_host.replace('s',''))
    elif 'h' in dst_host:
        dst_host = int(dst_host.replace('h',''))
        try:
            dst_sw = host_sw[dst_host]
            shortest_path.append('s'+str(dst_sw)+'-eth'+str(sw_port[dst_host]))
            shortest_path.append('h'+str(dst_host))
        except:
            shortest_path = []
            Error_list.append('h'+str(dst_host)+' is nonexist')
    return src_sw,dst_sw,Error_list,shortest_path,begin_insert

def Get_ShortestRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert):
    #获取网络所有链路的列表
    url = 'http://localhost:8080/wm/topology/links/json'
    links = json.loads(urllib2.urlopen(url).read())
    #获取拓扑的交换机链路信息形成图
    topo = set(tuple())
    sw = set()
    src_dst_sw_port = dict(tuple())
    for link in links:
        src = int(link['src-switch'].replace(':',''),16)
        dst = int(link['dst-switch'].replace(':',''),16)
        src_port = int(link['src-port'])
        dst_port = int(link['dst-port'])
        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))
        topo.add((src,dst))
        sw.add(src)
        sw.add(dst)
#    print src_dst_sw_port
    G = nx.Graph()
    for s in sw:
        G.add_node(s)
    G.add_edges_from(topo)
#    pos = nx.spectral_layout(G)
#    nx.draw(G,pos,with_labels=True,node_size = 1,font_size=24,font_color='red')
#    plt.savefig("Graph.png")
    try:
        #生成最短路径
        t1=datetime.datetime.now().microsecond
        shortest_sw_path = nx.dijkstra_path(G,src_sw,dst_sw)
        t2=datetime.datetime.now().microsecond
        dijkstra_path_time=str(t2-t1)+"ms"
        t3=datetime.datetime.now().microsecond
        nx.all_pairs_shortest_path(G)[src_sw][dst_sw]
        t4=datetime.datetime.now().microsecond
        all_shortest_path_time=str(t4-t3)+"ms"
        def k_shortest_paths(G, source, target, k, weight=None):
            return list(islice(nx.shortest_simple_paths(G, source, target),k))
        t5=datetime.datetime.now().microsecond
        k_shortest_paths(G,src_sw,dst_sw,1)
        t6=datetime.datetime.now().microsecond
        k_shortest_paths_time=str(t6-t5)+"ms"
        if(len(shortest_sw_path)>=2):
    #        print shortest_sw_path
            for i in range(0,len(shortest_sw_path)-1):
                src_sw = str(shortest_sw_path[i])
                dst_sw = str(shortest_sw_path[i+1])
                src_dst_port = None
                src_dst_port = src_dst_sw_port.get(src_sw+dst_sw)
                if(src_dst_port is None):
                    src_dst_port = src_dst_sw_port.get(dst_sw+src_sw)
                    shortest_path.insert(begin_insert,'s'+src_sw+'-eth'+str(src_dst_port[1]))
                    begin_insert += 1
                    shortest_path.insert(begin_insert,'s'+dst_sw+'-eth'+str(src_dst_port[0]))
                    begin_insert += 1
                else:
                    shortest_path.insert(begin_insert,'s'+src_sw+'-eth'+str(src_dst_port[0]))
                    begin_insert += 1
                    shortest_path.insert(begin_insert,'s'+dst_sw+'-eth'+str(src_dst_port[1]))
                    begin_insert += 1
        shortest_path.append({"dijkstra_path_time":dijkstra_path_time})
        shortest_path.append({"all_shortest_path_time":all_shortest_path_time})
        shortest_path.append({"k_shortest_paths_time":k_shortest_paths_time})
    except Exception,e:
        print e
        Error_list = []
        shortest_path = []
        if 's' not in src_host:
            src_host = 'h' + src_host
        if 's' not in dst_host:
            dst_host = 'h' + dst_host
        Error_list.append(str(src_host)+' to '+str(dst_host)+' unreachable')
    return Error_list,shortest_path

if __name__ == '__main__':
    src_host = str(sys.argv[1])
    dst_host = str(sys.argv[2])
    Error_list = list()
    shortest_path = list()
    begin_insert = 0
    src_sw,dst_sw,Error_list,shortest_path,begin_insert = Get_SrcSw_DstSw(src_host,dst_host,Error_list,shortest_path,begin_insert)
    if(len(Error_list) is 0):
        Error_list,shortest_path = Get_ShortestRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert)
    if(len(Error_list) is 0 and len(shortest_path) is 0):
        Error_list.append('unknown error')
        print json.dumps(Error_list)
    elif(len(Error_list) is not 0 and len(shortest_path) is 0):
        print json.dumps(Error_list)
    elif(len(Error_list) is 0 and len(shortest_path) is not 0):
        print json.dumps(shortest_path)