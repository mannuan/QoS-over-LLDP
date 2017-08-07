# -*- coding: utf-8 -*-
'''
Author wjl

usage:
    Parameters
    ----------
    sw_count : integer
             number of switches (default=3)
    link_count_per_sw : integer
             number of connections for each switch (default=3)
    host_count : integer
             number of hosts (default=2)
    host_count_per_sw : integer
             number of hosts connected to each switch
    
'''


import networkx as nx
import matplotlib.pyplot as plt
import random

from mininet.topo import Topo

#    sw_count * link_count_per_sw must be even
#    link_count_per_sw < sw_count
class MeshTopo(Topo):
    def __init__(self,sw_count=3,link_count_per_sw=2,host_count=2,host_count_per_sw=1):
        Topo.__init__(self)
        self.sw_count = sw_count
        self.link_count_per_sw = link_count_per_sw
        self.CreateTopoGraph(host_count,host_count_per_sw)
        
        
    def CreateTopoGraph(self,hc,hcps):
        def Remainder(hc, hcps):
            if hc % hcps == 0:
                return 0
            else:
                return 1
        #生成包含sw_count个节点、每个节点有link_count_per_sw个邻居的规则图RG
        RG = nx.random_graphs.random_regular_graph(self.link_count_per_sw,self.sw_count)
        #定义一个布局，此处采用了spectral布局方式，后变还会介绍其它布局方式，注意图形上的区别
#        pos = nx.spectral_layout(RG)
        #绘制规则图的图形，with_labels决定节点是非带标签（编号），node_size是节点的直径
#        nx.draw(RG,pos,with_labels=False,node_size = 40)
        #显示图形
#        plt.show()
#        plt.savefig("RegularGraph.png")
        switches = []
        for s in range(self.sw_count):
            switches.append(self.addSwitch('s'+str(s+1)))
        for (s1,s2) in list(RG.edges()):
            self.addLink(switches[s1],switches[s2])
        sw_count_conn_host = hc/hcps + Remainder(hc, hcps)
        switches_conn_host = []
        for scch in range(sw_count_conn_host):
            randint = random.randint(0,len(switches)-1)
            switches_conn_host.append(switches[randint])
            del(switches[randint])
        print switches
        print switches_conn_host
        hosts = []
        for h in range(hc):
            hosts.append(self.addHost('h'+str(h+1)))
        print hosts
        for i in range(hc/hcps):
            for j in range(hcps):
                self.addLink(switches_conn_host[i],hosts[0])
                del(hosts[0])
        for i in range(Remainder(hc, hcps)):
            for host in hosts:
                self.addLink(switches_conn_host[sw_count_conn_host-1],host)

topos = {'mytopo' : ( lambda : MyTopo() ) }