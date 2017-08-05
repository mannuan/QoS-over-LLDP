# -*- coding: utf-8 -*-
"""
Created on Sun May  1 20:16:42 2016
这是第四题的拓扑
@author: wjl
"""
from mininet.topo import Topo

class MyTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        H1 = self.addHost('H1')
        H2 = self.addHost('H2')
        D1 = self.addHost('D1')
        D2 = self.addHost('D2')
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s4 = self.addSwitch('s4')
        self.addLink(s1,H1)
        self.addLink(s1,H2)
        self.addLink(s1,s2)
        self.addLink(s1,s3)
        self.addLink(s4,s2)
        self.addLink(s4,s3)
        self.addLink(s4,D1)
        self.addLink(s4,D2)
        
topos = {'mytopo' : ( lambda : MyTopo() ) }
    
