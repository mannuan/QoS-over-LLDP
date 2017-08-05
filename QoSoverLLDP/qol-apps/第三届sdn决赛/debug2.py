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
from mininet.topolib import TreeTopo
from mininet.link import TCLink,TCIntf
from mininet.cli import CLI
from myutil import StartFloodLight,StopFloodLight

if __name__ == '__main__':
#    progress = StartFloodLight()
    setLogLevel('info')
    mytopo = TreeTopo()
    mytopo.build(2,2)
    network = Mininet(topo = mytopo,controller = RemoteController,autoSetMacs=True,link=TCLink,intf=TCIntf)
    network.addController('debug2',ip="127.0.0.1:6653")
    network.start()
    ports = []
    for sw in network.switches:
        for port in sw.ports:
            ports.append(port)
            if port.name is not 'lo':
#                port.config(bw=random.randint(10, 999),delay=random.randint(10, 10000),
#                            jitter=random.randint(10, 10000),loss=random.randint(1, 10))
                port.config(bw=100,delay=9,jitter=1,loss=0.0001)
#    CLI(network)
    print '\n超你妈'
    print network.get('h1').Cmd('xterm')
#    time.sleep(30)
#    network.stop()
#    StopFloodLight(progress)
    os.system('sudo mn -c')
