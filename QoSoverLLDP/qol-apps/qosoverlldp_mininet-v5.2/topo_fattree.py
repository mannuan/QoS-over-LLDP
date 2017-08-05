# -*- coding: utf-8 -*-
"""
Created on Wed May 24 10:15:08 2017

@author: mininet
"""
from mininet.topo import Topo
from mininet.log import info
import os

class Fattree(Topo):
    info("Class Fattree")
    CoreSwitchList = []
    AggSwitchList = []
    EdgeSwitchList = []
    HostList = []

    def __init__(self, k, density):  # K 为fattree pod个数。 density是tor下的主机个数。
        info("Class Fattree init")
        self.pod = k 
        self.iCoreLayerSwitch = (k/2)**2
        self.iAggLayerSwitch = k*k/2
        self.iEdgeLayerSwitch = k*k/2
        self.density = density
        self.iHost = self.iEdgeLayerSwitch * density

        #Init Topo
        Topo.__init__(self)

    def createTopo(self):
        self.createCoreLayerSwitch(self.iCoreLayerSwitch)
        self.createAggLayerSwitch(self.iAggLayerSwitch)
        self.createEdgeLayerSwitch(self.iEdgeLayerSwitch)
        self.createHost(self.iHost)

    """
    Create Switch and Host
    """

    def _addSwitch(self, number, level, switch_list):
        for x in xrange(1, number+1):
            PREFIX = str(level) + "00"
            if x >= int(10):
                PREFIX = str(level) + "0"
            switch_list.append(self.addSwitch('s' + PREFIX + str(x)))

    def createCoreLayerSwitch(self, NUMBER):
        info("Create Core Layer")
        self._addSwitch(NUMBER, 1, self.CoreSwitchList)

    def createAggLayerSwitch(self, NUMBER):
        info("Create Agg Layer")
        self._addSwitch(NUMBER, 2, self.AggSwitchList)

    def createEdgeLayerSwitch(self, NUMBER):
        info("Create Edge Layer")
        self._addSwitch(NUMBER, 3, self.EdgeSwitchList)

    def createHost(self, NUMBER):
        info("Create Host")
        for x in xrange(1, NUMBER+1):
            PREFIX = "h00"
            if x >= int(10):
                PREFIX = "h0"
            elif x >= int(100):
                PREFIX = "h"
            self.HostList.append(self.addHost(PREFIX + str(x)))

    """
    Add Link   createLink函数用于创建links,修改了原始版本写死的代码。
    """

    def createLink(self, bw_c2a=0.2, bw_a2e=0.1, bw_h2a=0.5):
        info("Add link Core to Agg.")
        end = self.pod/2
        for x in xrange(0, self.iAggLayerSwitch, end):
            for i in xrange(0, end):
                for j in xrange(0, end):
                    self.addLink(
                        self.CoreSwitchList[i*end+j],
                        self.AggSwitchList[x+i],bw=bw_c2a)

        info("Add link Agg to Edge.")
        for x in xrange(0, self.iAggLayerSwitch, end):
            for i in xrange(0, end):
                for j in xrange(0, end):
                    self.addLink(
                        self.AggSwitchList[x+i], self.EdgeSwitchList[x+j],bw=bw_a2e)

        info("Add link Edge to Host.")
        for x in xrange(0, self.iEdgeLayerSwitch):
            for i in xrange(0, self.density):
                self.addLink(
                    self.EdgeSwitchList[x],
                    self.HostList[self.density * x + i],bw=bw_h2a)

    def set_ovs_protocol_13(self,):
        self._set_ovs_protocol_13(self.CoreSwitchList)
        self._set_ovs_protocol_13(self.AggSwitchList)
        self._set_ovs_protocol_13(self.EdgeSwitchList)

    def _set_ovs_protocol_13(self, sw_list):
        for sw in sw_list:
            cmd = "sudo ovs-vsctl set bridge %s protocols=OpenFlow13" % sw
            os.system(cmd)