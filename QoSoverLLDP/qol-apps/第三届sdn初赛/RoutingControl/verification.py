# -*- coding: utf-8 -*-
"""
Created on Sun May  1 11:14:31 2016
这是一个路由验证的程序

@author: wjl
"""
from forth_topo import MyTopo
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.log import setLogLevel
from myutil import (ChangeNetConnintoLinkList,DisplayInfoOfNet,StartFloodLight,judgeIP,
                    DumpFlowstoFiles,StopFloodLight,CheckSwitchFlowBy98bytes,
                    PathSrctoDst,ProcessControl,DynamicOperateFlows)
import os


    
if __name__ == '__main__':
    progress = StartFloodLight()#开启floodlight
    pathsrctodst = {'H1->D1':'s1->s2->s4','H1->D2':'s1->s3->s4',
                 'H2->D1':'s1->s3->s4','H2->D2':'s1->s2->s4'}
    #创建mininet对象
    setLogLevel('info')
    mytopo = MyTopo()
    network = Mininet(topo = mytopo, controller = RemoteController,autoSetMacs=True)
    network.addController('verification')
    network.start()#启动mininet
    hosts = network.hosts#网络中的所有主机
    switches = network.switches#网络中的所有交换机
    while(True):#输入源ip与目的ip进行验证
#        os.system('bash forth_addflow.sh')#把静态流表推送到控制器
        DynamicOperateFlows(network,pathsrctodst,action='addstaticflow')
        host = DisplayInfoOfNet(network)#打印网络信息，并获取主机ip与名称的一个字典
        netconnlist = ChangeNetConnintoLinkList(network)#把网络的链路信息转化成链路列表
        #输入ip
        inputboole = True
        while(inputboole):
            srcip = raw_input('\n请输入源IP:')
            inputboole = judgeIP(srcip)
        inputboole = True
        while(inputboole):
            dstip = raw_input('\n请输入目的IP:')
            inputboole = judgeIP(dstip)
            if(srcip.find(dstip) == 0):
                inputboole = True
                print '\n源IP与目的IP不能重复!'
        #开始ping
        print('\n\n'+srcip+' ping '+dstip+' -c4')
        srchostnamelist = str(host[srcip])
        dsthostnamelist = str(host[dstip])
        srchostname = srchostnamelist[2]+srchostnamelist[3]
        dsthostname = dsthostnamelist[2]+dsthostnamelist[3]
        srchost = network.get(srchostname)
        dsthost = network.get(dsthostname)
        srchost.cmdPrint('ping',dsthost.IP(),'-c4')
        DumpFlowstoFiles(switches)#把交换机的流表输出到文件
        #开始根据流表分析路径
        path = srchostname+'->'
        for i in range(1,len(switches)+1):
            switch = 's'+str(i)
            path+=CheckSwitchFlowBy98bytes(switch)
        path+=dsthostname
        print '\n根据上面每个交换机的流表信息我们可以看出源主机到目的主机的路径为:\n'
        if(len(path)<=10):
            print srchostname+' 不能到达 '+dsthostname+'\n'
        else:
            path = PathSrctoDst(path,network,srchostname,dsthostname)
            print '路线:'+path+'\n'
        print '\n回车,删除静态流表以免对下面的测试造成影响'
        ProcessControl()
        DynamicOperateFlows(network,pathsrctodst,action='delstaticflow')
#        os.system('bash forth_delflow.sh')
        #判断是否结束验证程序
        print "\n请问你是否还要继续验证?\n"
        if raw_input('y=继续,回车=退出 :').find('y') != 0:
            break
    network.stop()#退出mininet
    os.system('sudo mn -c')#清理mininet
    StopFloodLight(progress)#关闭floodlight
            
        
