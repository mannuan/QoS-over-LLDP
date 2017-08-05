# -*- coding: utf-8 -*-
"""
@author: wjl
"""
from mininet.topo import Topo

class MyTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        ringlength = 3
        center = self.addSwitch('s'+str(ringlength+1))
        ringswitch = []
        for i in range(1,ringlength+1):
            s = self.addSwitch('s'+str(i))
            self.addLink(s,center)
            ringswitch.append(s)
        
        for i in range(0,ringlength-1):
            self.addLink(ringswitch[i],ringswitch[i+1])
        self.addLink(ringswitch[0],ringswitch[ringlength-1])
        
topos = {'mytopo' : ( lambda : MyTopo() ) }
    
