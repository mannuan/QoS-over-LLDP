# -*- coding: utf-8 -*-
"""
Created on Sun May  1 20:16:42 2016
这是第四题的拓扑
@author: wjl
"""
from mininet.topo import Topo

class TempletTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        h4 = self.addHost('h4')
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s4 = self.addSwitch('s4')
        self.addLink(s1,h1)
        self.addLink(s1,h2)
        self.addLink(s1,s2)
        self.addLink(s1,s3)
        self.addLink(s4,s2)
        self.addLink(s4,s3)
        self.addLink(s4,h3)
        self.addLink(s4,h4)
        
topos = {'templettopo' : ( lambda : TempletTopo() ) }
    
