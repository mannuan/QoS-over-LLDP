# -*- coding: utf-8 -*-
"""
Created on Sun May  1 20:16:42 2016
这是第三题的拓扑图
@author: wjl
"""

from mininet.topo import Topo

class MyTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        H1 = self.addHost('H1')
        H2 = self.addHost('H2')
        H3 = self.addHost('H3')
        H4 = self.addHost('H4')
        Web = self.addHost('Web')
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        self.addLink(H1,s1)
        self.addLink(H2,s1)
        self.addLink(H3,s1)
        self.addLink(H4,s1)
        self.addLink(s1,s2)
        self.addLink(s2,Web)

topos = {'mytopo' : ( lambda : MyTopo() ) }
    