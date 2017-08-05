# -*- coding: utf-8 -*-
"""
Created on Wed May  4 17:23:44 2016

@author: wjl
"""
import os
import sys
import random,time
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.log import setLogLevel
from mininet.topo import LinearTopo
from mininet.link import TCLink,TCIntf
from mininet.cli import CLI
from myutil import StartFloodLight,StopFloodLight

if __name__ == '__main__':
    progress = StartFloodLight()
    setLogLevel('info')
    mytopo = LinearTopo()
    mytopo.build(x=4,y=6)
    network = Mininet(topo = mytopo,controller = RemoteController,autoSetMacs=True,
                        link=TCLink,intf=TCIntf)
    network.addController('debug3',ip="127.0.0.1:6653")
    network.start()
    ports = []
    for sw in network.switches:
        for port in sw.ports:
            ports.append(port)
            port.config(bw=random.randint(10, 999),delay=random.randint(10, 10000),
                    jitter=random.randint(10, 10000),loss=random.randint(1, 10))
    CLI(network)
#    time.sleep(30)
    network.stop()
    StopFloodLight(progress)
    os.system('sudo mn -c')
