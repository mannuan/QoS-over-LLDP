# -*- coding: utf-8 -*-
"""
@author: wjl
"""
from mininet.topo import Topo

class RingTopo(Topo):
    def __init__(self,ringlength=8,hostnum=8):
        Topo.__init__(self)
#        ringlength:环交换机的数量
        center = self.addSwitch('s'+str(ringlength+1))
        ringswitch = []
        for i in range(1,ringlength+1):
            s = self.addSwitch('s'+str(i))
            self.addLink(s,center)
            ringswitch.append(s)
        
        for i in range(0,ringlength-1):
            self.addLink(ringswitch[i],ringswitch[i+1])
        self.addLink(ringswitch[0],ringswitch[ringlength-1])
        for i in range(0,hostnum):
            h = self.addHost('h'+str(i+1))
            self.addLink(h,ringswitch[i%len(ringswitch)])
        
topos = {'ringtopo' : ( lambda : RingTopo() ) }
    
