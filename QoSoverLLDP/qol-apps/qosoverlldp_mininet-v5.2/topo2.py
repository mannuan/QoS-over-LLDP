# -*- coding: utf-8 -*-
"""
Created on Mon Apr 17 11:58:05 2017

@author: mininet
"""

from mininet.topo import Topo

class MyTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s4 = self.addSwitch('s4')
        s5 = self.addSwitch('s5')
        s6 = self.addSwitch('s6')
        s7 = self.addSwitch('s7')
        self.addLink(h1,s1)
        self.addLink(s1,s2)
        self.addLink(s2,s3)
        self.addLink(s2,s4)
        self.addLink(s3,h2)
        self.addLink(s4,h3)
        
topos = {'mytopo' : ( lambda : MyTopo() ) }