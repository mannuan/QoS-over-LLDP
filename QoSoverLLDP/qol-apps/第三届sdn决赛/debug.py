# -*- coding: utf-8 -*-
"""
Created on Wed May  4 17:23:44 2016

@author: wjl
"""
import sys
import random,time
#sys.path.append("/home/wjl/mininet/mininet")
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.log import setLogLevel
from mininet.cli import CLI
from mininet.link import TCLink,TCIntf
from topo1 import MyTopo
from myutil import StartFloodLight,StopFloodLight

import os

if __name__ == '__main__':
#    progress = StartFloodLight()
    setLogLevel('info')
    mytopo = MyTopo()
    network = Mininet(topo = mytopo,controller = RemoteController,
                      link=TCLink,intf=TCIntf)
    network.addController('debug')
    network.start()
#    ports = []
#    for sw in network.switches:
#        for port in sw.ports:
#            ports.append(port)
#            port.config(bw=random.randint(10, 999),delay=random.randint(10, 10000),
#                    jitter=random.randint(10, 10000),loss=random.randint(1, 10))
    CLI(network)
#    time.sleep(150)
    network.stop()
#    StopFloodLight(progress)
    os.system('sudo mn -c')
    
#注意带宽最大999MB,loss最好设置小一点否则收不到packet_in