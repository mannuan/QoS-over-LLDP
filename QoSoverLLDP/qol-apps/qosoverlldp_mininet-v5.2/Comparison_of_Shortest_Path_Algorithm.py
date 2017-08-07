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
    
'''
import networkx as nx
import pandas as pd
import numpy as np
import time
from itertools import islice

#    sw_count * link_count_per_sw must be even
class MeshTopo(object):
    def __init__(self,sw_count=3,link_count_per_sw=2):
        self.sw_count = sw_count
        self.link_count_per_sw = link_count_per_sw
    def CreateTopoGraph(self):
        #生成包含sw_count个节点、每个节点有link_count_per_sw个邻居的规则图RG
        RG = nx.random_graphs.random_regular_graph(self.link_count_per_sw,self.sw_count)
        return RG
def Interval(G, src=0, dst=2):
    t1=time.time()
    nx.dijkstra_path(G,src,dst)
    t2=time.time()
    dijkstra_path_time=t2-t1
    t3=time.time()
    nx.all_pairs_shortest_path(G)[src][dst]
    t4=time.time()
    all_shortest_path_time=t4-t3
    def k_shortest_paths(G, source, target, k, weight=None):
        return list(islice(nx.shortest_simple_paths(G, source, target),k))
    t5=time.time()
    k_shortest_paths(G,src,dst,1)
    t6=time.time()
    k_shortest_paths_time=t6-t5
    if((dijkstra_path_time<0)
        |(all_shortest_path_time<0)
        |(k_shortest_paths_time<0)
        |(dijkstra_path_time>all_shortest_path_time)
        |(k_shortest_paths_time>all_shortest_path_time)):
        return []
    return [k_shortest_paths_time,dijkstra_path_time,all_shortest_path_time]
    
if __name__ == '__main__':
    counter = 0
    dataframelist = [[],[],[],[],[],[],[]]
    for i in range(1,3):
       sw_count = 100*i
       for j in range(11,41):
           link_count_per_sw = j
           meshtopo = MeshTopo(sw_count,link_count_per_sw)
           timelist=Interval(meshtopo.CreateTopoGraph(),0,sw_count-1)
           if(timelist!=[]):
               counter = counter + 1
               templist = ['S%d'%(0+1),'S%d'%(sw_count),sw_count,link_count_per_sw]
               templist.extend(timelist)
               for i in range(len(dataframelist)):
                   dataframelist[i].append(templist[i])
               print templist
    dataframe = pd.DataFrame(
            np.concatenate((
            np.array(dataframelist[0],dtype=np.object)[:,np.newaxis],
            np.array(dataframelist[1],dtype=np.object)[:,np.newaxis],
            np.array(dataframelist[2],dtype=np.int64)[:,np.newaxis],
            np.array(dataframelist[3],dtype=np.int64)[:,np.newaxis],
            np.array(dataframelist[4],dtype=np.float128)[:,np.newaxis],
            np.array(dataframelist[5],dtype=np.float128)[:,np.newaxis],
            np.array(dataframelist[6],dtype=np.float128)[:,np.newaxis]
            ),axis=1),
            index=[range(1,counter+1)],
            columns=['src','dst','sw_count','link_count_per_sw',
                     'k_shortest_paths_time (unit:s)','dijkstra_path_time (unit:s)','all_shortest_path_time (unit:s)'])
    dataframe.to_excel('Comparison_of_Shortest_Path_Algorithm.xls')